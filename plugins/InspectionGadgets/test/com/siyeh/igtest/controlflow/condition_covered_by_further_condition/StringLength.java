package com.siyeh.igtest.controlflow.pointless_null_check;

import org.jetbrains.annotations.NotNull;

public class StringLength {
  void test(String str) {
    if (<warning descr="Condition 'str.isEmpty()' covered by subsequent condition 'str.length() < 3'">str.isEmpty()</warning> || str.length() < 3) {}
  }

  void test2(String str) {
    if (str.length() > 8 && str.charAt(8) == 'a') {}
  }
}