package pkg;

class TestAnonymousClassConstructor {
  void innerPrivateString() {
    new InnerPrivateString("text"){};
  }

  void innerPrivate() {
    new InnerPrivate(3, 4){};
  }

  void innerStaticPrivateString() {
    new InnerStaticPrivateString("text"){};
  }

  void innerStaticPrivate() {
    new InnerStaticPrivate(3, 4){};
  }

  static void innerStaticPrivateStringStatic() {
    new InnerStaticPrivateString("text"){};
  }

  static void innerStaticPrivateStatic() {
    new InnerStaticPrivate(3, 4){};
  }

  void innerPublicString() {
    new InnerPublicString("text"){};
  }

  void innerPublic() {
    new InnerPublic(3, 4){};
  }

  void innerStaticPublicString() {
    new InnerStaticPublicString("text"){};
  }

  void innerStaticPublic() {
    new InnerStaticPublic(3, 4){};
  }

  static void innerStaticPublicStringStatic() {
    new InnerStaticPublicString("text"){};
  }

  static void innerStaticPublicStatic() {
    new InnerStaticPublic(3, 4){};
  }

  static void n(String s) {
    System.out.println("n(): " + s);
  }

  class InnerPrivateString {
    private InnerPrivateString(String s) {
      n(s);
    }
  }

  class InnerPrivate {
    private InnerPrivate(long a, int b) {
      n(a + "+" + b);
    }
  }

  static class InnerStaticPrivateString {
    private InnerStaticPrivateString(String s) {
      n(s);
    }
  }

  static class InnerStaticPrivate {
    private InnerStaticPrivate(long a, int b) {
      n(a + "+" + b);
    }
  }

  class InnerPublicString {
    public InnerPublicString(String s) {
      n(s);
    }
  }

  class InnerPublic {
    public InnerPublic(long a, int b) {
      n(a + "+" + b);
    }
  }

  static class InnerStaticPublicString {
    public InnerStaticPublicString(String s) {
      n(s);
    }
  }

  static class InnerStaticPublic {
    public InnerStaticPublic(long a, int b) {
      n(a + "+" + b);
    }
  }
}
