package org.example.failed;

import org.junit.platform.suite.api.*;

@Suite
@SelectClasses({FailedTest.class})
public class SuiteFailed {
  @BeforeSuite
  public static void before() {
    throw new RuntimeException();
  }
}