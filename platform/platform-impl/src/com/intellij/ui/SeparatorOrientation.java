package com.intellij.ui;


public class SeparatorOrientation {
  public static final SeparatorOrientation HORIZONTAL = new SeparatorOrientation("HORIZONTAL");
  public static final SeparatorOrientation VERTICAL = new SeparatorOrientation("VERTICAL");

  private final String myName; // for debug only

  private SeparatorOrientation(String name) {
    myName = name;
  }

  public String toString() {
    return myName;
  }

}