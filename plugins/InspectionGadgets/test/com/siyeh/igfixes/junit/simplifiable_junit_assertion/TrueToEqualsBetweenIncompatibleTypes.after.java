import org.junit.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JUnit4TestCase {

  @Test
  public void testOne() {
      assertEquals("1", 1);
  }
}