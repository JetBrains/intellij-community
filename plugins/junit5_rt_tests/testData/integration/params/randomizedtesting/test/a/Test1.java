package a;
import com.carrotsearch.randomizedtesting.RandomizedTest;
import com.carrotsearch.randomizedtesting.annotations.Name;
import com.carrotsearch.randomizedtesting.annotations.ParametersFactory;
import org.junit.Test;

import java.util.Arrays;


public class Test1 extends RandomizedTest {
  private int value;

  public Test1(@Name("value") int value) {
    this.value = value;
  }

  @Test
  public void simple() {
    System.out.println("Test" + value);
  }

  @ParametersFactory
  public static Iterable<Object[]> parameters() {
    return Arrays.asList($$(
      $(1),
      $(2)));
  }

}