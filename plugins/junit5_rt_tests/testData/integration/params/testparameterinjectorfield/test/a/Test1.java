package a;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@org.junit.runner.RunWith(TestParameterInjector.class)
public class Test1 {

  @TestParameter({"1", "2"}) private int a;
  
  @org.junit.Test
  public void simple() {
    System.out.println("Test" + a);
  }

}