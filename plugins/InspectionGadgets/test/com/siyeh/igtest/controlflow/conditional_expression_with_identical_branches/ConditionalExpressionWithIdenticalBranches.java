package com.siyeh.igtest.controlflow.conditional_expression_with_identical_branches;

import java.util.Random;

class ConditionalExpressionWithIdenticalBranches {

  int one(boolean b) {
    return b ? 1 + 2 + 3 : 1 + 2 + 3;
  }

  int two(boolean b) {
    return b ? 1 + 2 : 1 + 2 + 3;
  }

  Class<String> three(boolean b) {
    return b ? java.lang.String.class : String.class;
  }

  int incomplete(boolean b) {
    return b?
  }

  void fuzzy() {
    String someString = new Random().nextBoolean() ? "2" + "q" + "1" : "2" + "qwe" + "1";
  }

  void fuzzy2() {
    Object someString = new Random().nextBoolean() ? (Object) "1" : (Object) "2";
  }

  void fuzzy3() {
    Object someString = new Random().nextBoolean() ? "21" + (Object) "1" : "21" + (Object) "2";
  }

  void fuzzy4(int[] ints) {
    int i = new Random().nextBoolean() ? ints[3] : ints[4];
  }

  void fuzzy5(String[] strings) {
    String s = new Random().nextBoolean()? "asd" + strings[2] : "qwe" + strings[2];
  }

  void fuzzy6() {
    int j = new Random().nextBoolean() ? 6 + someMethod("123", "") : 6 + someMethod("321", "");
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

      Item item1 = (i == 1 ? new Item("1") : new Item("2")); // warning here
    }
  }
}