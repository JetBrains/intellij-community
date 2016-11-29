/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.fixes.braces;

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
  public void testForEachKeyword() { doTest("for"); }
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
