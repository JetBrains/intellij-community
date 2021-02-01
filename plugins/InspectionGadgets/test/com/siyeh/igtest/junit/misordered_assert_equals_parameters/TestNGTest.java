import org.testng.Assert;
import org.testng.annotations.Test;

public class TestNGTest {

  @Test
  public void testSomeThings() {
    // TestNG order should be (actual, expected)
    Assert.assertEquals(actual(), "expected");
    Assert.<warning descr="Arguments to 'assertEquals()' in wrong order">assertEquals</warning>("hello", actual());
  }

  public static String actual() {
    return "hello";
  }
}