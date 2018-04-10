// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.braces;

import com.intellij.lang.java.JavaLanguage;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.ControlFlowStatementWithoutBracesInspection;

/**
 * @author Pavel.Dolgov
 */
public class ControlFlowStatementWithoutBracesFixTest extends IGQuickFixesTestCase {

  public void testSimpleIfBody() { doTest("if"); }
  public void testSimpleIfExpression() { doTest("if"); }
  public void testSimpleIfKeyword() { doTest("if"); }

  public void testFullIfBody() { doTest("if"); }
  public void testFullIfKeyword() { doTest("if"); }
  public void testFullIfElseBody() { doTest("else"); }
  public void testFullIfElseKeyword() { doTest("else"); }
  public void testFullIfMiddle() { assertQuickfixNotAvailable(getMessagePrefix()); }

  public void testDoBody() { doTest("do"); }
  public void testDoExpression() { doTest("do"); }
  public void testDoMiddle() { doTest("do"); }

  public void testForEachBody() { doTest("for"); }
  public void testForEachExpression() { doTest("for"); }
  public void testForEachKeyword() { System.out.println(getLanguageSettings(JavaLanguage.INSTANCE).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE);doTest("for"); }
  public void testForIndex() { doTest("for"); }

  public void testWhile() { doTest("while"); }
  public void testWhileOutside() { assertQuickfixNotAvailable(getMessagePrefix()); }

  public void testLadderInnerElse() { doTest("else"); }
  public void testLadderInnerFor() { doTest("for"); }
  public void testLadderInnerIf() { doTest("if"); }
  public void testLadderOuterElse() { doTest("else"); }
  public void testLadderOuterFor() { doTest("for"); }
  public void testLadderOuterIf() { doTest("if"); }
  public void testLadderOutside() { assertQuickfixNotAvailable(getMessagePrefix()); }

  public void testNewlineInBlock() {
    final boolean previous = getLanguageSettings(JavaLanguage.INSTANCE).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE;
    getLanguageSettings(JavaLanguage.INSTANCE).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    try {
      doTest("if");
    } finally {
      getLanguageSettings(JavaLanguage.INSTANCE).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = previous;
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRelativePath = "statement_without_braces";
  }

  @Override
  protected BaseInspection getInspection() {
    return new ControlFlowStatementWithoutBracesInspection();
  }

  protected void doTest(String keyword) {
    super.doTest(getMessage(keyword));
  }

  private static String getMessage(String keyword) {
    return InspectionGadgetsBundle.message("control.flow.statement.without.braces.message", keyword);
  }

  private static String getMessagePrefix() {
    final String message = InspectionGadgetsBundle.message("control.flow.statement.without.braces.message", "@");
    final int index = message.indexOf("@");
    if (index >= 0) return message.substring(0, index);
    return message;
  }
}
