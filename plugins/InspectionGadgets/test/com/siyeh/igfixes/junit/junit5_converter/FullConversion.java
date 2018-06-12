
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Assert;
import java.util.*;

public class Full<caret>Conversion {
  @Test
  public void testAssertions() {
    fail("fail");
    assertTrue("always", true);
  }

  @Test
  public void testMethodRefs() {
    List<Boolean> booleanList = new ArrayList<>();
    booleanList.add(true);

    booleanList.forEach(Assert::assertTrue);
  }
}
