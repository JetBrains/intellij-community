package pkg;

class TestInnerClassConstructor {
  void m() {
    new Inner("text");
  }

  void n(String s) {
    System.out.println("n(): " + s);
  }

  final class Inner {
    private Inner(String s) {
      n(s);
    }
  }
}
