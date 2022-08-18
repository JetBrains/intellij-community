// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.performance;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.LengthOneStringsInConcatenationInspection;

/**
 * @author Fabrice TIERCELIN
 */
public class LengthOneStringsInConcatenationFixTest extends IGQuickFixesTestCase {
  @SuppressWarnings("SingleCharacterStringConcatenation")
  public void testFirstConcatenationOperand() {
    doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"f\"/**/ + \"bar\"", "'f' + \"bar\"");
  }

  @SuppressWarnings("SingleCharacterStringConcatenation")
  public void testSecondConcatenationOperand() {
    doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"foo\" + /**/\"b\"", "\"foo\" + 'b'");
  }

  @SuppressWarnings("SingleCharacterStringConcatenation")
  public void testThirdConcatenationOperand() {
    doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"foo\" + 1 + /**/\"c\"", "\"foo\" + 1 + 'c'");
  }

  @SuppressWarnings("SingleCharacterStringConcatenation")
  public void testAppendMethodParameter() {
    doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "new StringBuilder().append(/**/\"c\")", "new StringBuilder().append('c')");
  }

  @SuppressWarnings("SingleCharacterStringConcatenation")
  public void testNewLine() {
    doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"\\n\"/**/ + \"bar\"", "'\\n' + \"bar\"");
  }

  @SuppressWarnings("SingleCharacterStringConcatenation")
  public void testQuote() {
    doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"\\'\"/**/ + \"bar\"", "'\\'' + \"bar\"");
  }

  @SuppressWarnings("SingleCharacterStringConcatenation")
  public void testDoubleQuote() {
    doExpressionTest(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                     "\"\\\"\"/**/ + \"bar\"", "'\"' + \"bar\"");
  }

  public void testDoNotFixWrongType() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                               "class X {\n" +
                               "  String field = /**/\"a\";\n" +
                               "}\n");
  }

  public void testDoNotFixIfConcatenationTurnsIntoAddition() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                               "class X {\n" +
                               "  String field = /**/\"a\" + 'b';\n" +
                               "}\n");
  }

  public void testDoNotFixIfSecondOperandTurnsConcatenationIntoAddition() {
    assertQuickfixNotAvailable(InspectionGadgetsBundle.message("length.one.strings.in.concatenation.replace.quickfix"),
                               "class X {\n" +
                               "  String field = 'a' + /**/\"b\";\n" +
                               "}\n");
  }

  @Override
  protected BaseInspection getInspection() {
    return new LengthOneStringsInConcatenationInspection();
  }
}
