package collect;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Test data for the "collect parameters without running bodies" integration tests.
 * Every test body appends to the file named by the {@code idea.junit.test.marker.file} system property; the collect pass
 * must leave that file empty (bodies skipped) while still reporting every invocation.
 */
public class CollectParametersTests {
  @ParameterizedTest
  @ValueSource(ints = {-1, 7, 0})
  public void parameterized(int value) {
    recordBodyExecution();
  }

  @TestFactory
  public Stream<DynamicTest> factory() {
    // The factory body itself must run to produce the names; only the dynamic test bodies below record execution.
    return Stream.of(
      dynamicTest("Test 1", CollectParametersTests::recordBodyExecution),
      dynamicTest("Test 2", CollectParametersTests::recordBodyExecution),
      dynamicTest("Test 3", CollectParametersTests::recordBodyExecution)
    );
  }

  static void recordBodyExecution() {
    String path = System.getProperty("idea.junit.test.marker.file");
    if (path == null) return;
    try {
      Files.write(Path.of(path), "x".getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
