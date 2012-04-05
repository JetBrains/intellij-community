package com.siyeh.igtest.classlayout.final_private_method;

public class FinalPrivateMethod {

  private final void foo() {};

  @java.lang.SafeVarargs
  private final void foo(String s) {}

  @java.lang.SafeVarargs
  private final void foo(int... i) {}
}
