package junit4;

import org.junit.Test;

public class MyTest2 { 

  @Test
  public void singleMethodTest2 () {
    org.junit.Assert.fail("junit 4: single method failed");
  }
}