import java.io.ByteArrayInputStream;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;

class Test {

  public void assertThatPositive1() {
    try {
      <warning descr="'assertThat()' cannot fail as it's suppressed by a surrounding 'catch'">assertThat(1).as("test").isEqualTo(1)</warning>;
    } catch (AssertionError e) {}
  }

  public void assertThatPositive2() {
    try {
      <warning descr="'assertThat()' cannot fail as it's suppressed by a surrounding 'catch'">assertThat(1).as("test").isEqualTo(1)</warning>;
    } catch (Exception e) {
    } catch (AssertionError e) {}
  }

  public void assertThatPositive3() {
    try {
      int a = 1;
      <warning descr="'assertThat()' cannot fail as it's suppressed by a surrounding 'catch'">assertThat(1).as("test").isEqualTo(1)</warning>;
    } catch (AssertionError e) {}
  }

  public void assertThatNegative1() {
    try {
      assertThat(1).as("test").isEqualTo(1);
      int a = 1;
    } catch (AssertionError e) {}
  }

  public void assertThatNegative2() {
    try {
      assertThat(1).as("test").isEqualTo(1);
    } catch (Exception e) {}
  }
}