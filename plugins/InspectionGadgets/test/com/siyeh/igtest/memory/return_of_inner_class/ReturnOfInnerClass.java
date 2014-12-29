public class ReturnOfInnerClass {

  public Object one() {
    <warning descr="Return of instance of anonymous class">return</warning> new Object() {};
  }

  public Object two() {
    class A {}
    <warning descr="Return of instance of local class 'A'">return</warning> new A();
  }

  class B {}
  public Object three() {
    <warning descr="Return of instance of non-static inner class 'B'">return</warning> new B();
  }

  private Object four() {
    return new B();
  }

  protected Object five() {
    return new B();
  }

  Object six() {
    return new B();
  }
}