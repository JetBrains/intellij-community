package test.misc;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;

public class ExtTest<E> extends ArrayList<E> {

	public static void main(String[] args) {
		
		Date d = new Date(); Timestamp t = new Timestamp(d.getTime());
		System.out.println(d.after(t)); 

		System.out.println((int)-1);
		System.out.println((int)1);
		System.out.println((Integer)1);
		System.out.println((Integer)1);
		
		Class c = String.class;
		
		Integer Integer = 0;
		Integer = (Integer)-1;
		
		int z = 0;
		z = z+++(++z);
		
		Entry ent = (Entry)new HashMap().entrySet().iterator().next();
		
		label1: {
		System.out.println("1");
		if(Math.random() > 4) {
			break label1;
		}
		System.out.println("2");
		}
		System.out.println("3");
	}

	public <T extends E> void test(T o) {
		
	}
	
	public void testException() throws IOException {
//		if(true) {
//			throw new RuntimeException();
//		} else {
//			throw new IOException();
//		}
//		throw true?new IOException():new IOException();
//		throw true?new ClassCastException():new IOException();
	}
	
	public static int ttt() {
		
		Object obj = new Integer(5);
		synchronized (new Integer(3)) {
			System.out.println(obj);
			obj = null;
		}
		
		
		System.out.println("1");
		if(Math.random() > 1) {
			System.out.println("2");
		} else {
			System.out.println("3");
		}
		System.out.println("4");
		
		int a = 0;
		try {
			a = 2;
			return a;
		} finally {
			a = 4;
		}
	}
}
