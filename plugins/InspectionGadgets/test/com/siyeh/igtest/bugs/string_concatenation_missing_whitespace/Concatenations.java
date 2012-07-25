package com.siyeh.igtest.bugs.string_concatenation_missing_whitespace;

class Concatenations {

  void foo(int i) {
    System.out.println("SELECT column" +
                       "FROM table");
    System.out.println("no:" + i);
    System.out.println("i" + i);
  }
}