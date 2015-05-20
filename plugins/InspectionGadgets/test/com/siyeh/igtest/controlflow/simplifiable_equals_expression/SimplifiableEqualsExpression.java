package com.siyeh.igtest.controlflow.simplifiable_equals_expression;

public class SimplifiableEqualsExpression {

  void foo(String namespace) {
    if (<warning descr="Unnecessary 'null' check before 'equals()' call">namespace != null</warning> && namespace.equals("")) {
      return;
    }
  }

  void bar(String namespace) {
    if (<warning descr="Unnecessary 'null' check before 'equals()' call">namespace == null</warning> || !namespace.equals("")) {
      return;
    }
  }

  void baz(Integer i) {
    if (<warning descr="Unnecessary 'null' check before 'equals()' call">i != null</warning> && i.equals(1)) {
      return;
    }
  }

  void boz(String namespace) {
    if (namespace == null || namespace.equals("")) { // don't warn here
      return;
    }
  }

  void bas(String s) {
    if (<warning descr="Unnecessary 'null' check before 'equalsIgnoreCase()' call">s != null</warning> && s.equalsIgnoreCase("yes")) {
      return;
    }
  }
}
