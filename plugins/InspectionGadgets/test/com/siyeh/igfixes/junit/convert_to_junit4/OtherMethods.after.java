import junit.framework.TestCase;
import org.junit.Assert;
import org.junit.Test;

public class OtherMethods {

  public void _testIgnored() {
    Assert.assertTrue(true);
  }

  public void testSomething(int i) {
    Assert.assertTrue(true);
  }

  @Test
  public void testBla() {
    Assert.assertFalse(false);
  }

  @org.junit.Test
  public void testForeign() {

  }
}