/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ipp.forloop;

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.controlflow.ForLoopReplaceableByWhileInspection;
import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceForLoopWithWhileLoopFixTest extends IPPTestCase {

  public void testLabeledForLoop() { doTest(); }
  public void testNotInBlock() { doTest(); }
  public void testDoubleLabelNoBraces() { doTest(); }
  public void testUpdatingMuch() { doTest(); }
  public void testContinuing() { doTest(); }
  public void testNoInit() { doTest(); }

  @Override
  protected String getIntentionName() {
    return InspectionGadgetsBundle.message("for.loop.replaceable.by.while.replace.quickfix");
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ForLoopReplaceableByWhileInspection());
  }

  @Override
  protected String getRelativePath() {
    return "forloop/while_loop";
  }
}