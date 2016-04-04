package pkg;

class TestInnerClassConstructor {
  void l() {
    new Inner("text");
  }

  void m() {
    new Another(3, 4);
  }

  void n(String s) {
    System.out.println("n(): " + s);
  }

  final class Inner {
    private Inner(String s) {
      n(s);
    }
  }

  private class Another {
    private Another(int a, int b) {
      n(a + "+" + b);
    }
  }
}
