package com.intellij.openapi.vcs;

public enum ThreeStateBoolean {
  yes,
  no,
  z;

  public static ThreeStateBoolean getInstance(final boolean value) {
    return value ? yes : no;
  }
}
