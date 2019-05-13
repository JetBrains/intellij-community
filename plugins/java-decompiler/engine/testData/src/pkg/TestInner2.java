package pkg;

class TestInner2 {
  private TestInner2() {}
  private TestInner2(int a) {}

  class Another extends TestInner2 {
    Another() {
    }
  }

  static class AnotherStatic extends TestInner2 {
    AnotherStatic() {
    }
  }

  class Another2 extends TestInner2 {
    Another2() {
      super(2);
    }
  }

  static class AnotherStatic2 extends TestInner2 {
    AnotherStatic2() {
      super(2);
    }
  }
}
