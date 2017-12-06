package com.siyeh.igtest.bugs.string_concatenation_missing_whitespace;

class Concatenations {

  void foo(int i) {
    System.out.println("SELECT column" <warning descr="String literal concatenation missing whitespace">+</warning>
                       "FROM table");
    System.out.println("no:" + i);
    System.out.println("i" <warning descr="String literal concatenation missing whitespace">+</warning> i);
    System.out.println("i" <warning descr="String literal concatenation missing whitespace">+</warning> ((String)"j"));
    System.out.println('{' + "a" + '\'');
    String.format("aaaa%n" + "bbbb");
  }
}