package org.example;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class WallTimeSuiteJUnit5Test {
  @BeforeAll
  static void setUpClass() throws InterruptedException {
    Thread.sleep(200);
  }

  @Test
  void test() throws InterruptedException {
    Thread.sleep(100);
  }
}
