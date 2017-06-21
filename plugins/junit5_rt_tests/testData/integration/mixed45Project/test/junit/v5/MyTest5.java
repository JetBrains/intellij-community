package junit.v5;

import org.junit.jupiter.api.Test;

public class MyTest5 { 

  @Test
  public void singleMethodTest () {
    org.junit.jupiter.api.Assertions.fail("junit 5: single method failed");
  }
}