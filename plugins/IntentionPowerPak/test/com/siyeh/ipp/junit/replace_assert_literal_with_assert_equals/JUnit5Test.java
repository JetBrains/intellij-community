import static org.junit.jupiter.api.Assertions.assertTrue;

public class JUnit5Test {

  @org.junit.jupiter.api.Test
  public void xxx() {
    <caret>assertTrue(true);
  }
}