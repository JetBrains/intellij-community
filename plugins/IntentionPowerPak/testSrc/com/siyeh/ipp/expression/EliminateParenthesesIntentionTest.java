// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.expression;

import com.intellij.openapi.editor.CaretModel;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

public class EliminateParenthesesIntentionTest extends IPPTestCase {

  public void testAssociative() {
    doTest();
  }

  public void testDistributive() {
    doTest();
  }

  public void testNestedParenthesis() {
    doTest();
  }

  @Override
  protected void doTest(@NotNull String intentionName) {
    String testName = getTestName(false);
    myFixture.configureByFile(testName + ".java");
    CaretModel model = myFixture.getEditor().getCaretModel();
    model.runForEachCaret(caret -> {
      model.moveToOffset(caret.getOffset());
      myFixture.launchAction(myFixture.findSingleIntention(intentionName));
    });
    myFixture.checkResultByFile(testName + "_after.java", false);
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("eliminate.parentheses.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "expression/eliminate_parentheses";
  }
}
