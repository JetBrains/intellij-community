// "Replace 'if else' with '?:'" "INFORMATION"
import java.util.Arrays;

class Test {
  void test(boolean b) {
      Arrays.asList("foo", "bar", b ? "baz" : "qux");
  }
}