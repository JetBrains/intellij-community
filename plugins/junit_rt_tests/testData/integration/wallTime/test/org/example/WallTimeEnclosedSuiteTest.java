package org.example;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

@RunWith(Enclosed.class)
public class WallTimeEnclosedSuiteTest {
  public static class InnerTest {
    @BeforeClass
    public static void setUpClass() throws InterruptedException {
      Thread.sleep(250);
    }

    @Test
    public void test1() throws InterruptedException {
      Thread.sleep(150);
    }

    @Test
    public void test2() throws InterruptedException {
      Thread.sleep(150);
    }
  }
}