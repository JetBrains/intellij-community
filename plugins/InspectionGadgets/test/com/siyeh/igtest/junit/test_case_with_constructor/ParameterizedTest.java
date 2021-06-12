import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ParameterizedTest {

  private final int myX;
  private final int myY;

  public ParameterizedTest(int x, int y) {
    myX = x;
    myY = y;
  }

  @Test
  public void testMe() {
    System.out.println(myX * myY);
  }

  @Parameterized.Parameters
  public static Object[][] parameters() {
    return new Object[][] {{1, 2}, {3, 4}};
  }
}
