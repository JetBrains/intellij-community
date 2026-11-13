package repeat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PassesInSecondRunTests {
  private static int ourRuns;

  @Test
  void test() {
    assertTrue(++ourRuns > 1, "the first run fails");
  }
}
