// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.jdk;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ForwardCompatibilityInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/jdk/forward_compatibility";
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new ForwardCompatibilityInspection();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  public void testAssert() { doTestWithLevel(LanguageLevel.JDK_1_3); }

  public void testEnum() { doTestWithLevel(LanguageLevel.JDK_1_3); }

  public void testUnqualifiedYield() { doTest(); }

  public void testUnderscore() { doTest(); }

  public void testVarClassesWarning() { doTest(); }

  public void testModuleInfoWarning() {
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_1_9, getTestRootDisposable());
    myFixture.configureByFile("module-info.java");
    myFixture.testHighlighting(true, false, false);
  }

  public void doTestWithLevel(LanguageLevel languageLevel) {
    IdeaTestUtil.setModuleLanguageLevel(getModule(), languageLevel, getTestRootDisposable());
    doTest();
  }
}
