class Foo {
  public static void main(String[] args) {
      try {
          new AddException().foo();
      } catch (java.io.IOException e) {
          throw new RuntimeException(e);
      }
  }
}