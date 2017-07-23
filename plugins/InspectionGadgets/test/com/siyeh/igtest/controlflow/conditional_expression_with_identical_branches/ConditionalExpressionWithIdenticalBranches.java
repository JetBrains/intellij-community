package com.siyeh.igtest.controlflow.conditional_expression_with_identical_branches;

class ConditionalExpressionWithIdenticalBranches {

  int one(boolean b) {
    return <warning descr="Conditional expression 'b ? 1 + 2 + 3 : 1 + 2 + 3' with identical branches">b ? 1 + 2 + 3 : 1 + 2 + 3</warning>;
  }

  int two(boolean b) {
    return b ? 1 + 2 : 1 + 2 + 3;
  }

  Class<String> three(boolean b) {
    return <warning descr="Conditional expression 'b ? java.lang.String.class : String.class' with identical branches">b ? java.lang.String.class : String.class</warning>;
  }

  int incomplete(boolean b) {
    return b?<EOLError descr="Expression expected"></EOLError><EOLError descr="';' expected"></EOLError>
  }

  int someMethod(String s, String s2) {
    return s.length();
  }

  class A {
    private String test(String... s) {
      return "";
    }

    private void test2(boolean f) {
      String a = f ? test(new String[]{"a", "b"}) : test("a");
    }
  }

  static class WithFunctionalExpression {
    private void foo(boolean b) {
      Runnable r = b ? (Runnable) () -> {} : (Runnable) () -> {};
      IntSupplier s = <warning descr="Conditional expression 'b ? () -> 1 : () -> { return 1; }' with identical branches">b ? () -> 1 : () -> { return 1; }</warning>;
    }
  }

  void lambdaCycle(boolean b){
    Runnable r = <warning descr="Conditional expression 'b ? () -> {if (true);} : () -> {if (true);}' with identical branches">b ? () -> {if (true);} : () -> {if (true);}</warning>;
  }

  interface IntSupplier {
    int getAsInt();
  }
}