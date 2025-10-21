package testSuite;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.platform.suite.api.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("important")
class SuiteTest {
  @Test
  void test1() {}

  @Test
  void test2() {
    assertTrue(false);
  }

  @Test
  @Tag("skip")
  void skippedTest() {}
}
