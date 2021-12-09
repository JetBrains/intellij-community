package patterns;

public class TestInstanceofBinaryExpr {

  void typePattern(Object str) {
    if (str instanceof String s && (s.length() > 1 || s.startsWith("a"))) {
      System.out.println(s);
    } else {
      System.out.println("no");
    }
  }
}