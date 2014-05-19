package test.util;

public class MemoryMonitor implements Runnable {

	public static boolean run = false;
	
	
	public void run() {
		
		while(run) {
			try {
				Thread.sleep(500);
			} catch(InterruptedException ex) {
				ex.printStackTrace();
			}
			
			//Runtime.getRuntime().gc();
			System.err.println((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())/(1024*1024));
			
		}
		
	}

	
}
