interface I {
  boolean <warning descr="'equals()' should take 'Object' as its argument">equals</warning>(I i);
}
class A {

  public boolean <warning descr="'equals()' should take 'Object' as its argument">equals</warning>(A a) {
    return false;
  }
}
class B extends A {

  @Override
  public boolean equals(A a) {
    return super.equals(a);
  }
}
class C {
  public boolean equals(C c) {
    return false;
  }

  public boolean equals(Object o) {
    return true;
  }
}
class D implements I {
  @Override
  public boolean equals(I i) {
    return false;
  }
}
class E {
  boolean <warning descr="'equals()' should take 'Object' as its argument">equals</warning>(E e) {
    return false;
  }
}