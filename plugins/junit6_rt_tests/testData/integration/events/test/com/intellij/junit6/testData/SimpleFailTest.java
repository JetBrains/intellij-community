package com.intellij.junit6.testData;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SimpleFailTest {
  @Test
  public void test1() {
    Assertions.fail("123");
  }

  @Test
  public void test2() {
    Assertions.assertEquals("123", "321");
  }
}