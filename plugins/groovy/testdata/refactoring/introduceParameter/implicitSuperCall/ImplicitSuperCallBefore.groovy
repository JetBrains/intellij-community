class B extends A {
  int k;

  public B() {
    k = 10;
  }
}

class Usage {
  A a = new B();
}