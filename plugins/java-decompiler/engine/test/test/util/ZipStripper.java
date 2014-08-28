package test.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipStripper {

	public static void main(String[] args) {

		try {
			String sourceFileName = args[0];
			File sourceFile = new File(sourceFileName);
			
			File tempFile = new File(sourceFile.getParentFile(), "tmp31415926535.zip");
			tempFile.createNewFile();
			
			ZipOutputStream outTemp = new ZipOutputStream(new FileOutputStream(tempFile));
			
			ZipFile archive = new ZipFile(sourceFile);
			
			Enumeration<? extends ZipEntry> en = archive.entries();
			while(en.hasMoreElements()) {
				ZipEntry entr = en.nextElement();
			
				outTemp.putNextEntry(new ZipEntry(entr.getName()));
				
				if(!entr.isDirectory()) {
					InputStream in = archive.getInputStream(entr);
					
					copyInputStream(in, outTemp);
					in.close();
				}
			}
			
			outTemp.flush();
			outTemp.close();
			
			archive.close();
			
			String destFileName = args[1];

			if(sourceFileName.equals(destFileName)) {
				sourceFile.delete();
			}
			
			tempFile.renameTo(new File(destFileName));
			
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		
	}

	public static void copyInputStream(InputStream in, OutputStream out)throws IOException {
		
		byte[] buffer = new byte[1024];
		int len;
		
		while((len = in.read(buffer)) >= 0) {
			out.write(buffer, 0, len);
		}
	}
	
}
