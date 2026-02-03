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

    test2("1");

    if(Math.random() > 0) {
      System.out.println("0");
      return 0;
    } else {
      System.out.println("1");
      return 1;
    }
  }

  public void test2(String a) {
    try {
      Integer.parseInt(a);
    } catch (Exception e) {
      System.out.println(e);
    } finally {
      System.out.println("Finally");
    }
  }

  public class InnerClass {
    public void print() {
      System.out.println("Inner");
    }
  }

  void run(Runnable r) {
    r.run();
  }

  public class InnerClass2 {
    public void print() {
      System.out.println("Inner2");
    }
  }
}
