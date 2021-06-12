import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("valid reason")
class DisabledJUnit5Test {

  @<warning descr="Test method 'addition()' annotated with 'Disabled'">Disabled</warning>
  @Test
  void addition() {
  }
}