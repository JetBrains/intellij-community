package com.siyeh.igtest.controlflow.simplifiable_equals_expression;

public class SimplifiableEqualsExpression {

  void foo(String namespace) {
    if (namespace != null && namespace.equals("")) {
      return;
    }
  }

  void bar(String namespace) {
    if (namespace == null || !namespace.equals("")) {
      return;
    }
  }

  void baz(Integer i) {
    if (i != null && i.equals(1)) {
      return;
    }
  }

  void boz(String namespace) {
    if (namespace == null || namespace.equals("")) { // don't warn here
      return;
    }
  }

  void bas(String s) {
    if (s != null && s.equalsIgnoreCase("yes")) {
      return;
    }
  }
}
