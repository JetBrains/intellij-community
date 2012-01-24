package com.siyeh.igtest.classlayout.public_constructor_in_non_public_class;

class PublicConstructorInNonPublicClass {

  public PublicConstructorInNonPublicClass () {}

  private class A {
    public A() {}
  }

  protected class B {
    public B() {}
  }
}
