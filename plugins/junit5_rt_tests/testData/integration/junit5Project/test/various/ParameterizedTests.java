package various;

import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParameterizedTests {
  @org.junit.jupiter.params.ParameterizedTest(name = "{0} + {1} = {2}")
  @CsvSource({
    "0,    1,   1",
    "1,    2,   3",
    "49,  51, 101",
    "1,  100, 101"
  })
  void add(int first, int second, int expectedResult) {
    assertEquals(expectedResult, first + second,
                 () -> first + " + " + second + " should equal " + expectedResult);
  }
}
