package test.misc;

public class MultipleClassTest {

	public int field = this.hashCode();
	
	public void test() {
		
		class Test1 {
			public int t1() {
//				System.out.println("1");
				
				try {
					return 2;
				} finally {
					System.out.println("1");
					return 3;
				}
				
			}
		}
		
		class Test2 {
			public void t2() {
				System.out.println("2");
				//new Test1().t1();
			}
		}

//		new Test1().t1();
	}
	
}
