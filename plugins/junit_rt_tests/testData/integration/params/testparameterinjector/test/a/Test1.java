package a;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

@org.junit.runner.RunWith(TestParameterInjector.class)
public class Test1 {


  @org.junit.Test
  public void simple(@TestParameter({"1", "2"}) int a) {
    System.out.println("Test" + a);
  }

}