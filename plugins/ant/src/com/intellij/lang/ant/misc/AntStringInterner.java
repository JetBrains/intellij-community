package com.intellij.lang.ant.misc;

import com.intellij.util.containers.StringInterner;

public class AntStringInterner {

  private static final StringInterner ourInterner = new StringInterner();

  public static String intern(final String str) {
    synchronized(ourInterner) {
      return ourInterner.intern(str);
    }
  }

  private AntStringInterner() {
  }
}
