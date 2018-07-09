// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ImplicitArrayToStringInspectionTest extends LightInspectionTestCase {

  public void testImplicitArrayToString() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ImplicitArrayToStringInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[] {
      "package java.util;" +
      "public class Formatter {" +
      "  public Formatter format(Locale l, String format, Object ... args) {" +
      "    return null;" +
      "  }" +
      "  public Formatter format(String format, Object ... args) {" +
      "    return null;" +
      "  }" +
      "}",

      "package java.io;" +
      "public class PrintWriter extends Writer {" +
      "  public PrintWriter(OutputStream out) {}" +
      "  public PrintWriter format(String format, Object ... args) {" +
      "    return null;" +
      "  }" +
      "}"
    };
  }
}