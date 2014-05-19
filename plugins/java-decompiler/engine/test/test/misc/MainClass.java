package test.misc;

import java.util.ArrayList;
import java.util.List;

public class MainClass {

	private int intfield;
	
	private static int stfield;
	
	private static void statmeth(int t) {
		System.out.println(t);
	}
	
	private String maintest(int v) {
		System.out.println(v+"test!");
		return "";
	}
	
	public MainClass() {
		super();
	}
	
	public void mainclosure() {
		
		final int local1 = (int)Math.random();
		
		List l = new ArrayList(new ArrayList()) {
			
			{
				System.out.println(intfield);
			}
			
			public void anontest() {
				System.out.println(local1);
			}
		};
		
		class LocalClass {
			
			public LocalClass(String s) {
				System.out.println(s);
				statmeth(4);
			}
			
			public void localtest() {
				int i = intfield;
				intfield = 4;
				maintest(i);

				System.out.println(local1);
				
				System.out.println(stfield);
				stfield = 3;
			}
			
			class LocalMemberClass {
				
			}
			
		}
		
		boolean t = false;
		if(t) {
			LocalClass lc = new LocalClass("1");
			LocalClass.LocalMemberClass rt = lc.new LocalMemberClass();;
		} else {
			if(Math.random() > 1) {
				LocalClass lc1 = new LocalClass("1");
			}
			System.out.println();
		}
		
	}
	
	
	public class MemberClass {
		
		public MemberClass(String z) {
			System.out.println(z);
		}
		
		public void membertest() {
			int i = intfield;
			maintest(i);
			stfield = 5;
			
			Member2Class.Member3Class t = (new Member2Class()).new Member3Class();
		}

		public class Member2Class1 {

			public class Member3Class1 {
				
			}
		}
		
		class Member2Class {
			
			public void member2test() {
				int i = intfield;
				maintest(i);
				Member2Class1.Member3Class1 t;
			}
			
			public class Member3Class {
				
			}
			
		}
		
	}
	
}
