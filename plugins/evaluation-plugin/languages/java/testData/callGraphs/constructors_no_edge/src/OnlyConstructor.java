public class OnlyConstructor {
  static class Inner {
    Inner() {}
  }
  public void method() {
    new Inner();
  }
}
