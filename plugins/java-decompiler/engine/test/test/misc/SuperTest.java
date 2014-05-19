package test.misc;

import java.util.ArrayList;

public class SuperTest extends ArrayList {

	public SuperTest() {
		super(3);
		super.modCount = 2;
		SuperTest.super.size();
	}
	
	public int size() {
		System.out.println("1");
		return 0;
	}
	
	class SuperTest1 {
		
		public void test() {
			SuperTest.super.size();
			SuperTest.super.modCount = 2;
		}
		
	}
	
}
