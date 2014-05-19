package test.misc;

public class ParentFieldTest {

	public int test = 0;
	
	private class Parent extends ParentFieldTest {

		private class Child {
			
			public void test() {
				System.out.println(ParentFieldTest.this.test);
			}
			
		}
		
	}
	
}
