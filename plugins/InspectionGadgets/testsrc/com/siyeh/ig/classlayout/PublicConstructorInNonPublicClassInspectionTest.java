// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class PublicConstructorInNonPublicClassInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/classlayout/public_constructor_in_non_public_class";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_14;
  }

  private void doTest() {
    myFixture.enableInspections(new PublicConstructorInNonPublicClassInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testPublicConstructorInNonPublicClass() {
    doTest();
  }
  public void testRecordsJava15() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_15_PREVIEW, this::doTest);
  }

  public void testQuickfix() {
    doTest();
    final IntentionAction intention = myFixture.getAvailableIntention("Remove 'public' modifier");
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + ".after.java");
  }
}