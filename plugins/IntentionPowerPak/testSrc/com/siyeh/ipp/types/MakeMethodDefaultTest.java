// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.types;

import com.intellij.codeInsight.intention.IntentionAction;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MakeMethodDefaultTest extends IPPTestCase {
 
  @Override
  protected String getIntentionName() {
    return "Make 'foo()' default";
  }

  @Override
  protected String getRelativePath() {
    return "types/makeDefault";
  }

  public void testInInterface() {
    doTest();
  }

  public void testAlreadyDefault() {
    assertIntentionNotAvailable();
  }

  public void testAlreadyHasBody() {
    @NotNull final String intentionName = getIntentionName();
    final String testName = getTestName(false);
    myFixture.configureByFile(testName + ".java");
    final List<IntentionAction> actions = myFixture.filterAvailableIntentions(intentionName);
    for (IntentionAction action : actions) {
      // a quick fix with the same name is available if the method already has a body
      // so we can't check on the name
      assertFalse(action instanceof MakeMethodDefaultIntention);
    }
  }
  
  public void testAnnotationType() {
    assertIntentionNotAvailable();
  }
}
