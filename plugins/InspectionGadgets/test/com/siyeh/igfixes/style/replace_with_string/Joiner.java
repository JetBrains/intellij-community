
import java.util.StringJoiner;

class Test {
  void test(String str1, String str2) {
    String s = new Strin<caret>gJoiner("") // comment0
      .add(str1) // comment1
      .add(str2) // comment2
      .toString(); // comment3
  }
}