package com.siyeh.igtest.classlayout.public_constructor_in_non_public_class;

class PublicConstructorInNonPublicClass {

  <warning descr="Constructor is declared 'public' in non-public class 'PublicConstructorInNonPublicClass'">public</warning> PublicConstructorInNonPublicClass () {}

  private class A {
    <warning descr="Constructor is declared 'public' in non-public class 'A'">public</warning> A() {}
  }

  protected class B {
    public B() {}
  }
}
