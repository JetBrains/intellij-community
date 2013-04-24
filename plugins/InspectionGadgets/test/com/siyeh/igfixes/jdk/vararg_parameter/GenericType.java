package com.siyeh.igfixes.jdk.vararg_parameter;

@SuppressWarnings("UnusedDeclaration")
public class GenericType {
  @java.lang.SafeVarargs
  final void addCl<caret>asses(Class<? extends Number>... classes) {
  }

  void test() {
    addClasses(Number.class, Byte.class);
  }
}