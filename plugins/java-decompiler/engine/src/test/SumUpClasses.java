package test;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public class SumUpClasses {

	public static void main(String[] args) {

		try {
			File dir = new File("C:\\revjava\\remote\\data\\input\\");
			
			long[] res = getClasses(dir);
			
			System.out.println("Count: "+res[0]);
			System.out.println("Size: "+res[1]/1024/1024);
			
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	}
	
	private static long[] getClasses(File file) {
		
		if(file.isDirectory()) {
			
			long count = 0, size = 0;
			
			for(File f : file.listFiles()) {
				long[] arr = getClasses(f);
				count+=arr[0];
				size+=arr[1];
			}
			
			return new long[] {count, size};
			
		} else {
			String filename = file.getName();
			if(filename.endsWith(".class")) {
				return new long[] {1, file.length()};
			} else if(filename.endsWith(".zip")) {
				try {
					return getClassesZip(new ZipFile(file));
				} catch(IOException ex) {
					System.out.println("Cannot read file: " + file.getAbsolutePath());
				}
			} else if(filename.endsWith(".jar")) {
				try {
					return getClassesZip(new JarFile(file));
				} catch(IOException ex) {
					System.out.println("Cannot read file: " + file.getAbsolutePath());
				}
			}
		}
		
		return new long[] {0, 0};
	}
	
	private static long[] getClassesZip(ZipFile archive) {
		
		long count = 0, size = 0;
		
		Enumeration<? extends ZipEntry> en = archive.entries();
		while(en.hasMoreElements()) {
			ZipEntry entr = (ZipEntry)en.nextElement();

			if(!entr.isDirectory() && entr.getName().endsWith(".class")) {
				count++;
				size+=entr.getSize();
			}
		}
		
		return new long[] {count, size};
	}

}
