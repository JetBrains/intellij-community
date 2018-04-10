
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.TestInstance;

@TestInstance( TestInstance.Lifecycle.PER_CLASS )
@interface Meta{}

@Meta
abstract class AbstractTest {}
class TestWithMethodSource extends AbstractTest {
  @ParameterizedTest
  @MethodSource("getParameters")
  public void shouldExecuteWithParameterizedMethodSource(String arguments) { }

  public Stream getParameters(){ //non static but that's expected due to PER_CLASS test instance
    return Arrays.asList( "Another execution", "Last execution").stream();
  }
}