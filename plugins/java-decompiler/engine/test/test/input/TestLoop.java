package test.input;

public class TestLoop {

	public static void main(String[] args) {

		boolean a = true;
		while(true) {
			try {
				if(!a) {
					return; 
				}
			} finally {
				System.out.println("1");
			}
		}
	
	}
	
}
