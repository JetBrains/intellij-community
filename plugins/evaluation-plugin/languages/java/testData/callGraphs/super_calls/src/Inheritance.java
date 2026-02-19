class Base {
  public void baseMethod() { }
}

class Derived extends Base {
  public void caller() {
    super.baseMethod();
  }
}
