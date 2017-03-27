public class ClassEscapesItsScope<T> {

  public T t;

  public <warning descr="Class 'A' is exposed outside its defined scope">A</warning> giveMeA() {
    return new A();
  }
  void printA(<warning descr="Class 'A' is exposed outside its defined scope">A</warning> a) {
    System.out.println(a);
  }
  private class A {}

  void throwsE() throws <warning descr="Class 'E' is exposed outside its defined scope">E</warning> {
    throw new E();
  }
  private static class E extends Exception {}
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
    public <warning descr="Class 'Bar' is exposed outside its defined scope">Bar</warning> bar;
    public <warning descr="Class 'Bar' is exposed outside its defined scope">Bar</warning> apply(String s) {
      throw new UnsupportedOperationException();
    }
  }

  interface F<T, R> {
    R apply(T t);
  }
}