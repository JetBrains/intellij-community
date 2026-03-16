package checkClasspath;

import org.junit.jupiter.api.Test;

class CheckerTest {
  @Test
  void test() {
    try {
      Class.forName("com.intellij.junit6.JUnit6IdeaTestRunner");
      throw new AssertionError("JUnit 6 rt should not be present in the classpath");
    }
    catch (ClassNotFoundException e) {
    }
  }
}