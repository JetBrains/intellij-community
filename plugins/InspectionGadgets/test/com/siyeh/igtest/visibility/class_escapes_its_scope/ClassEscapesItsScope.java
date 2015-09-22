public class ClassEscapesItsScope<T> {

  public T t;

  public <warning descr="Class 'A' is made visible outside its defined scope">A</warning> giveMeA() {
    return new A();
  }
  private class A {}
}

class BarInside {
  private static class Bar {}
  void foo() {
    class LocalClass implements F<String, Bar> {
      public Bar bar;
      public Bar apply(String s) {
        throw new UnsupportedOperationException();
      }
    }
  }

  class InnerClass implements F<String, Bar> {
    public <warning descr="Class 'Bar' is made visible outside its defined scope">Bar</warning> bar;
    public <warning descr="Class 'Bar' is made visible outside its defined scope">Bar</warning> apply(String s) {
      throw new UnsupportedOperationException();
    }
  }

  interface F<T, R> {
    R apply(T t);
  }
}