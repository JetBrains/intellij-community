// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryFinalOnLocalVariableOrParameterInspectionTest extends LightJavaInspectionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7;
  }

  public void testUnnecessaryFinalOnLocalVariableOrParameter() {
    doTest();
  }

  public void testUnnecessaryFinalOnPatternVariable() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_20_PREVIEW, this::doTest);
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryFinalOnLocalVariableOrParameterInspection();
  }
}
