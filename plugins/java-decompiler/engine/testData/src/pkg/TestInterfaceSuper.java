package pkg;

public interface TestInterfaceSuper {
  default void defaultMethod() {}

  class Impl implements TestInterfaceSuper {
    public void defaultMethod() {
      TestInterfaceSuper.super.defaultMethod();
    }
  }
}