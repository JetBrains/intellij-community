package com.siyeh.igtest.controlflow.conditional_expression_with_identical_branches;

import java.util.Random;

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

  void fuzzy() {
    String someString = <warning descr="Conditional expression 'new Random().nextBoolean() ? \"2\" + \"q\" + \"1\" : \"2\" + \"qwe\" + \"1\"' with similar branches">new Random().nextBoolean() ? "2" + "q" + "1" : "2" + "qwe" + "1"</warning>;
  }

  void fuzzy2() {
    Object someString = <warning descr="Conditional expression 'new Random().nextBoolean() ? (Object) \"1\" : (Object) \"2\"' with similar branches">new Random().nextBoolean() ? (Object) "1" : (Object) "2"</warning>;
  }

  void fuzzy3() {
    Object someString = <warning descr="Conditional expression 'new Random().nextBoolean() ? \"21\" + (Object) \"1\" : \"21\" + (Object) \"2\"' with similar branches">new Random().nextBoolean() ? "21" + (Object) "1" : "21" + (Object) "2"</warning>;
  }

  void fuzzy4(int[] ints) {
    int i = <warning descr="Conditional expression 'new Random().nextBoolean() ? ints[3] : ints[4]' with similar branches">new Random().nextBoolean() ? ints[3] : ints[4]</warning>;
  }

  void fuzzy5(String[] strings) {
    String s = <warning descr="Conditional expression 'new Random().nextBoolean()? \"asd\" + strings[2] : \"qwe\" + strings[2]' with similar branches">new Random().nextBoolean()? "asd" + strings[2] : "qwe" + strings[2]</warning>;
  }

  void fuzzy6() {
    int j = <warning descr="Conditional expression 'new Random().nextBoolean() ? 6 + someMethod(\"123\", \"\") : 6 + someMethod(\"321\", \"\")' with similar branches">new Random().nextBoolean() ? 6 + someMethod("123", "") : 6 + someMethod("321", "")</warning>;
  }

  int someMethod(String s, String s2) {
    return s.length();
  }

  class Item {
    Item(String name) {
    }

    Item(int value) {
    }

    void v() {
      int i = 1;
      Item item = (i == 1 ? new Item("1") : new Item(i)); // warning here

      Item item1 = (<warning descr="Conditional expression 'i == 1 ? new Item(\"1\") : new Item(\"2\")' with similar branches">i == 1 ? new Item("1") : new Item("2")</warning>); // warning here
    }
  }

  class A {
    private String test(String... s) {
      return "";
    }

    private void test2(boolean f) {
      String a = f ? test(new String[]{"a", "b"}) : test("a");
    }
  }
}