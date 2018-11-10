package com.siyeh.igfixes.performance.replace_with_isempty;

public class NullCheckAlreadyPresent {
  public void someAction(boolean flag, String s1, String s2) {
    if ((fl<caret>ag ? s1 : s2) != null && (flag ? s1 : s2).isEmpty()) {
      System.out.println("");
    }
  }
}