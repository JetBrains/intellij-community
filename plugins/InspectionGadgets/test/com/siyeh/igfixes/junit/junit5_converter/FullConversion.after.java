import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class FullConversion {
  @Test
  public void testAssertions() {
    fail("fail");
    assertTrue(true, "always");
  }

  @Test
  public void testMethodRefs() {
    List<Boolean> booleanList = new ArrayList<>();
    booleanList.add(true);

    booleanList.forEach(Assertions::assertTrue);
  }
}
