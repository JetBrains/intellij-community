package patterns;

public class TestInstanceof {

  void typePattern(Object str) {
    if (str instanceof String s) {
      System.out.println(s);
    } else {
      System.out.println("no");
    }
  }
}