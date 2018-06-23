
import java.util.StringJoiner;

class Test {
  void test2(String s, CharSequence s1) {
      String sj = "foo" + // comment1
              s + // comment2
              "bar" + // comment3
              s1;
      System.out.println(sj // comment4
      );
  }
}