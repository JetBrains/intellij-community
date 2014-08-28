package test.misc.en;

public class FinallyTest {

	public FinallyTest() {
		
		int i;
		try {
			try {
				i = 0;
			} finally {
				i = 1; 
			}
			i = 2;
		} finally {
			i = 3; 
		}
		
		System.out.println(i);
	}

}
