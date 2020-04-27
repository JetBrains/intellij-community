// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;

@ReviseWhenPortedToJDK("9")
public final class StringFactory {
  // String(char[], boolean). Works since JDK1.7, earlier JDKs have too slow reflection anyway
  private static final Constructor<String> ourConstructor;

  static {
    Constructor<String> constructor = null;
    // makes no sense in JDK9 because new String(char[],boolean) there parses and copies array too.
    if (!SystemInfoRt.IS_AT_LEAST_JAVA9) {
      try {
        constructor = String.class.getDeclaredConstructor(char[].class, boolean.class);
        constructor.setAccessible(true);
      }
      catch (Throwable ignored) {
        constructor = null; // setAccessible fails without explicit permission on Java 9
      }
    }
    ourConstructor = constructor;
  }

  /**
   * @return new instance of String which backed by given char array.
   *
   * CAUTION! EXTREMELY DANGEROUS!! DO NOT USE THIS METHOD UNLESS YOU ARE REALLY DESPERATE!!!
   */
  @NotNull
  public static String createShared(char @NotNull [] chars) {
    if (ourConstructor != null) {
      try {
        return ourConstructor.newInstance(chars, Boolean.TRUE);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    return new String(chars);
  }
}