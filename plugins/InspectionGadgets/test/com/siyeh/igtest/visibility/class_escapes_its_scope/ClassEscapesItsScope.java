public class ClassEscapesItsScope<T> {

  public T t;

  public <warning descr="Class 'A' is made visible outside its defined scope">A</warning> giveMeA() {
    return new A();
  }
  private class A {}
}