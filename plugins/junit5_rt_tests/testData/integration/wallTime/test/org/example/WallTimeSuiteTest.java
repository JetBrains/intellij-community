package org.example;

import org.junit.AfterClass;
import org.junit.Test;

public class WallTimeSuiteTest {
  @AfterClass
  public static void tearDownClass() throws InterruptedException {
    Thread.sleep(200);
  }

  @Test
  public void test() throws InterruptedException {
    Thread.sleep(100);
  }
}