package junit4;

import org.junit.Test;

public class MyTest { 

  @Test
  public void singleMethodTest () {
    org.junit.Assert.fail("junit 4: single method failed");
  }
}