package com.siyeh.igfixes.performance.replace_with_isempty;

public class NullCheckAlreadyPresent {

  String[] splitProperties = {"1","2"};

  public void someAction() {
    if (splitProperties[1] != null && splitProperties[1].isEmpty()) {
      System.out.println("");
    }
  }
}