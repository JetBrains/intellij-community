package test.misc.en;

public class InnerTestOld {

	public static void main(String[] args) {

		final String test = args[0];
		final int test1 = Integer.parseInt(args[1]);
		
		Runnable r = new Runnable() {
			public void run() {
				System.out.println(test);
				System.out.println(test1);
			}
		};
		
		System.out.println("done");
	}

}
