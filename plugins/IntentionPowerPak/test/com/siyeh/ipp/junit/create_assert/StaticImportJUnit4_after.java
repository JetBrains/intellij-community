import static java.util.Collections.EMPTY_LIST;
import static org.junit.Assert.assertNotNull;

public class AnonymousClassJUnit4 {

  @org.junit.Test
  public void testNotNull() {
      assertNotNull(EMPTY_LIST);
  }
}