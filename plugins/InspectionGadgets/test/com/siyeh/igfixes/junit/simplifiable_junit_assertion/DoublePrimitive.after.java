import org.junit.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DoublePrimitive {

  @Test
  public void testPrimitive() {
      assertEquals(2.0, doubleValue(), 0.0);
  }

  Double doubleValue() {
    return 1.0;
  }
}