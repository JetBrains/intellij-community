package pkg;

import java.lang.Override;
import java.lang.Runnable;

public class TestClassSimpleBytecodeMapping {

  public TestClassSimpleBytecodeMapping() {}
  
  public int test() {
    
    System.out.println("before");

    run(new Runnable() {
      @Override
      public void run() {
        System.out.println("Runnable");
      }
    });

    if(Math.random() > 0) {
      System.out.println("0");
      return 0;
    } else {
      System.out.println("1");
      return 1;
    }
  }

  void run(Runnable r) {
    r.run();
  }
}
