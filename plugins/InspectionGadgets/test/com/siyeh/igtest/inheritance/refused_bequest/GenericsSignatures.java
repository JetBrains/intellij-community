class A<C> {
  public boolean foo(C value) {
    return true;
  }
}

class B extends A<String> {
  @Override
  public boolean foo(String sValue) {
    super.foo(sValue);
    return true;
  }
}