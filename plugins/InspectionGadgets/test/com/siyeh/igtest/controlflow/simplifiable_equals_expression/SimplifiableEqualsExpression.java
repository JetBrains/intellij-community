package com.siyeh.igtest.controlflow.simplifiable_equals_expression;

import java.util.*;

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

  private Optional<Long> getOptional() {
    return Optional.of(1L);
  }

  // IDEA-177798 Simplifiable equals expression: support non-constant argument
  public boolean foo(Long previousGroupHead) {
    Long aLong = getOptional().get();

    return <warning descr="Unnecessary 'null' check before 'equals()' call">previousGroupHead != null</warning> && previousGroupHead.equals(aLong);
  }

  void trimTest(String s1, String s2) {
    if(<warning descr="Unnecessary 'null' check before 'equals()' call">s1 == null</warning> || !s1.equals(s2.trim())) {
      System.out.println("...");
    }
  }

  void test(List<String> list) {
    String s = list.get(0);
    if(s != null && s.equals(list.get(1))) {
      System.out.println("???");
    }
  }
}
