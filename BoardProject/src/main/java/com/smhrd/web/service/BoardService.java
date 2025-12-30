package com.smhrd.web.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.smhrd.web.config.NCPBucketConfig;
import com.smhrd.web.dto.BoardRequestDTO;
import com.smhrd.web.entity.Board;
import com.smhrd.web.repository.BoardRepository;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class BoardService {

    private final NCPBucketConfig NCPBucketConfig;
	
	@Autowired
	private S3Client s3Client;
	
	@Value("${ncp.end-point}")
	private String endPoint;
	
	@Value("${ncp.bucket-name}")
	private String bucketName;
	
	@Value("${file.upload-dir}")
	private String uploadDir;
	
	//1. repository 객체 생성
	@Autowired
	private BoardRepository repository;

    BoardService(NCPBucketConfig NCPBucketConfig) {
        this.NCPBucketConfig = NCPBucketConfig;
    }
	
	//2. DB관련 기능 구현
	// 게시글 전체보기 기능
	public List<Board> getList() {
		return repository.findAll();
	}
	
	//3. 게시글 작성 기능(파일업로드 포함/로컬저장)
	//- 파일업로드 기능을 구현할 때, 하나의 Entity로 처리 X
	//- 요청정보를 담는 별도의 DTO 클래스를 만들어서 처리
	//- Entity는 DB와 연동되는 모델 유지
	//- Entity 구조의 노출 위험
	//- 파일 저장 정책(NCP Object Storage, AWS S3)이 변경되었을 때 Entity까지 영향
//	public void register(BoardRequestDTO dto) throws Exception {
//		
//		//1. 로컬위치에 저장하는 로직 구현
//		String savedPath = null; // 업로드 후 저장될 경로
//		MultipartFile file = dto.getB_file(); // 요청DTO에 담긴 파일 가져오기
//		
//		if(file != null && !file.isEmpty()) {
//			
//			//1. 파일명 충돌 방지를 위한 로직 구현
//			String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
//			
//			//2. 서버에 저장할 폴더명
//			// * 실제 저장할 위치에 폴더 생성해두기!
//			Path uploadFolder = Paths.get(uploadDir);
//			
//			//3. 실제 저장될 파일 경로
//			Path savePath = uploadFolder.resolve(fileName);
//			
//			//4. 파일 저장
//			Files.copy(file.getInputStream(), savePath, StandardCopyOption.REPLACE_EXISTING);
//			
//			System.out.println(uploadDir);
//			System.out.println(savePath);
//			
//			// DB에 저장할 경로 생성
//			savedPath = uploadDir + fileName;
//		}
//		
//		//2. Entity객체를 생성해서 DB에 저장
//		Board board = new Board();
//		board.setB_title(dto.getB_title());
//		board.setB_writer(dto.getB_writer());
//		board.setB_content(dto.getB_content());
//		board.setB_file_path(savedPath);
//		
//		repository.save(board);
//	}
	
	// 3. 게시글 작성 기능 ( 파일 업로드 / NCP Object Storage)
	public void register(BoardRequestDTO dto) throws Exception {
		//1. 로컬위치에 저장하는 로직 구현
		String savedPath = null; // 업로드 후 저장될 경로
		MultipartFile file = dto.getB_file(); // 요청DTO에 담긴 파일 가져오기
		
		if(file != null && !file.isEmpty()) {
			
			// 1. 파일명 충돌 방지를 위한 로직 구현
			String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
			
			// 2. NCP에 저장하기 위한 요청 객체 생성
			PutObjectRequest putObjReq = PutObjectRequest.builder()
										.bucket(bucketName)
										.key(fileName)
										.contentType(file.getContentType())
										.acl(ObjectCannedACL.PUBLIC_READ)
										.build();
			
			// 3. NCP Object Storage로 전달
			s3Client.putObject(putObjReq, RequestBody.fromBytes(file.getBytes()));
			
			// 4. DB에 저장할 파일 경로 저장
			savedPath = endPoint + "/" + bucketName + "/" + fileName;
			System.out.println("저장경로: " + savedPath);
		}
		
		//2. Entity객체를 생성해서 DB에 저장
		Board board = new Board();
		board.setB_title(dto.getB_title());
		board.setB_writer(dto.getB_writer());
		board.setB_content(dto.getB_content());
		board.setB_file_path(savedPath);
		
		repository.save(board);
	}
	
	// 4. 게시글 상세보기
	public Board getDetail(Long b_idx) {
		
		// findById() : 특정 데이터 조회
		Optional<Board> optBoard = repository.findById(b_idx);
		
		// isEmpty() : 값이 비워져있는지를 체크하는 메소드
		if(optBoard.isEmpty()) {
			throw new IllegalArgumentException("게시글이 존재하지 않습니다.");
		}
		
		return optBoard.get();
	}
	
	
	public ResponseEntity<Resource> download(Long b_idx) throws Exception {
		// TODO Auto-generated method stub
		
		Optional<Board> optBoard = repository.findById(b_idx);
		
		// isEmpty() : 값이 비워져있는지를 체크하는 메소드
		if(optBoard.isEmpty()) {
			throw new IllegalArgumentException("게시글이 존재하지 않습니다.");
		}
		
		Board board = optBoard.get();
		
		String file_path = board.getB_file_path();  // NCP Object Storage
		
		if(file_path == null || file_path.isBlank()) {
			
			// 파일 경로변수가 null이거나 비워져 있는 경우 404 오류를 보내기
			return ResponseEntity.notFound().build();
		}
		
		System.out.println(file_path);
		
		//URL에서 파일명 추출
		//URL 구조 : NCP Object Storage의 endPoint + "/" + bucketName + "/" + fileName(key)
		
		String marker = "/"+bucketName+"/"; // --> myapp-obj-sg/
		int idx = file_path.indexOf(marker);
		
		//실제 파일명(key) 접근
		String key = file_path.substring(idx + marker.length());
		System.out.println("파일명: " + key);
		
		// NCP Object Storage에 저장된 파일을 요청하는 객체 생성
		GetObjectRequest getObjReq = GetObjectRequest.builder()
									.bucket(bucketName)
									.key(key)
									.build();
		
		// 파일 요청하기
		ResponseInputStream<GetObjectResponse> objStream = s3Client.getObject(getObjReq);
		GetObjectResponse meta = objStream.response();
		
		// 파일 -> byte[]로 변환
		byte[] bytes = objStream.readAllBytes();
		ByteArrayResource resource = new ByteArrayResource(bytes);
		
		// 다운로드 파일명 :
		// 현재 파일명 : 알파벳 + 숫자조합_파일명.확장자 -> 파일명.확장자
		// dfc24615-9480-46e6-94da-e90812b88643_running.jpg
		String filename = key.contains("_") ? key.substring(key.lastIndexOf("_") + 1) : key;
		System.out.println(filename);
		
		// content-disposition 헤더
		// -> 응답을 브라우저가 어떻게 처리할지 알려주는 설명서
		// attachment -> 무조건 다운로드
		// filename -> 다운로드할 파일명
		ContentDisposition cd = ContentDisposition.attachment()
												.filename(filename, StandardCharsets.UTF_8)
												.build();
		
		//최종 응답 처리
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.contentLength(bytes.length)
				.body(resource);
	}
}
