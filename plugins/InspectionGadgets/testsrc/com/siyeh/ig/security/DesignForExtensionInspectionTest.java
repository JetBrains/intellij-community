// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.security;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class DesignForExtensionInspectionTest extends LightJavaInspectionTestCase {

  public void testDesignForExtension() {
    doTest();
    final IntentionAction intention = myFixture.getAvailableIntention("Make method 'equals()' 'final'");
    assertNotNull(intention);
    myFixture.checkPreviewAndLaunchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + ".after.java");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new DesignForExtensionInspection();
  }
}