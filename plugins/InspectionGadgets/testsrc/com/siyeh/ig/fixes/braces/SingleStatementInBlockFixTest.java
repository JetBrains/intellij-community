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
import com.siyeh.ig.style.SingleStatementInBlockInspection;

/**
 * @author Bas Leijdekkers
 * @author Pavel.Dolgov
 */
public class SingleStatementInBlockFixTest extends IGQuickFixesTestCase {

  public void testBetweenIfAndElse() { assertQuickfixNotAvailable(getMessagePrefix());}
  public void testIfElse() { doTest("if"); }
  public void testIfElse2() { doTest("if"); }
  public void testIfElse3() { doTest("else"); }
  public void testWhile() { doTest("while"); }
  public void testForEach() { doTest("for"); }
  public void testForIndex() { doTest("for"); }
  public void testForMalformed() { assertQuickfixNotAvailable(getMessage("for"));}
  public void testDoWhile() { doTest("do"); }
  public void testIfWithLoop() { doTest("if"); }
  public void testElseWithLoop() { doTest("else"); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRelativePath = "single_statement_block";
  }

  @Override
  protected BaseInspection getInspection() {
    return new SingleStatementInBlockInspection();
  }

  protected void doTest(String keyword) {
    super.doTest(getMessage(keyword));
  }

  private static String getMessage(String keyword) {
    return InspectionGadgetsBundle.message("single.statement.in.block.quickfix", keyword);
  }

  private static String getMessagePrefix() {
    final String message = InspectionGadgetsBundle.message("single.statement.in.block.quickfix", "@");
    final int index = message.indexOf("@");
    if (index >= 0) return message.substring(0, index);
    return message;
  }
}
