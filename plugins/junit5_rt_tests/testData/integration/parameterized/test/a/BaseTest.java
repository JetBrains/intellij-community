package a;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;

@RunWith(Parameterized.class)
public abstract class BaseTest {

    @Parameterized.Parameters
    public static List<Integer> params() {
        return Arrays.asList(1, 2);
    }

    private int myField;
    public BaseTest(int i) {
        myField = i;
    }

    @Test
    public void simple() throws Exception {
      System.out.println(getClass().getName() + myField);
    }

  @Test
  public void testSome2() throws Exception {
    System.out.println("another");
  }
}
