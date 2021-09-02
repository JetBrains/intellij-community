// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryStringEscapeInspectionTest extends LightJavaInspectionTestCase {

  public void testEndOfTextBlockQuote() { doQuickFixTest(); }
  public void testNewlinesAndQuotes() { doQuickFixTest(); }
  public void testDoubleQuoteInChar() { doQuickFixTest(); }
  public void testSingleQuoteInString() { doQuickFixTest(); }
  public void testMultipleProblemsInSingleString() { doQuickFixTest(); }
  public void testEscapedNewLine() { doTest(); }

  public void testEscapedNewLineNotUnnecessary() { doTest(); }
  public void testBrokenCode() { doTest(); }

  protected void doQuickFixTest() {
    doTest();
    checkQuickFixAll();
  }

  @Override
  protected Class<? extends InspectionProfileEntry> getInspectionClass() {
    return UnnecessaryStringEscapeInspection.class;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final UnnecessaryStringEscapeInspection inspection = new UnnecessaryStringEscapeInspection();
    inspection.reportChars = true;
    return inspection;
  }

}