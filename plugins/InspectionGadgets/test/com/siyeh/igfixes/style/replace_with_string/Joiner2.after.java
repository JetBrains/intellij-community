
import java.util.StringJoiner;

class Test {
  void test2(String s, CharSequence s1) {
      // comment1
      // comment2
      // comment3
      // comment4
      String sj = "foo" +
              s +
              "bar" +
              s1;
      System.out.println(sj);
  }
}