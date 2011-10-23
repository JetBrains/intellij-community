package com.siyeh.igtest.bugs.string_equality;

public class StringEquality {

  void foo(String s, String t) {
    final boolean a = s == null;
    final boolean b = t == s;
    final boolean c = t ==
  }
}
