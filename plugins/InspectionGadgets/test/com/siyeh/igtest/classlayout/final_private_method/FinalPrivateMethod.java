package com.siyeh.igtest.classlayout.final_private_method;

public class FinalPrivateMethod {

  private <warning descr="'private' method declared 'final'">final</warning> void foo() {};

  <error descr="@SafeVarargs is not allowed on methods with fixed arity">@java.lang.SafeVarargs</error>
  private <warning descr="'private' method declared 'final'">final</warning> void foo(String s) {}

  @java.lang.SafeVarargs
  private final <T> void foo(T... i) {}
}
