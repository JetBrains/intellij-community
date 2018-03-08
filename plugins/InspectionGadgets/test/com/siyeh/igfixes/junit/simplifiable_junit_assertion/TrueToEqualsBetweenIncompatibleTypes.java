import org.junit.*;

import static org.junit.Assert.assertTrue;

public class JUnit4TestCase {

  @Test
  public void testOne() {
    <caret>assertTrue("1".equals(1));
  }
}