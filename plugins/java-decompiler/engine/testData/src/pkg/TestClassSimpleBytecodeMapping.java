package pkg;

public class TestClassSimpleBytecodeMapping {

  public TestClassSimpleBytecodeMapping() {}
  
  public int test() {
    
    System.out.println("before");
        
    if(Math.random() > 0) {
      System.out.println("0");
      return 0;
    } else {
      System.out.println("1");
      return 1;
    }
  }

}
