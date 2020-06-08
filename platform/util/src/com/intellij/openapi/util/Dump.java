// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.util;

import java.io.PrintStream;

public final class Dump {

  public static void out(String text) {
    print(text, System.out);
  }

  public static void err(String text) {
    print(text, System.err);
  }

  private static void print(String text, PrintStream ps) {
    Exception e = new Exception();
    StackTraceElement[] element = e.getStackTrace();
    StackTraceElement dumper = element[2];
    ps.println(text + " at " + dumper.toString());
  }
}
