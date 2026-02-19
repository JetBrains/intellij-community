// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public abstract class InspectionFixtureTestCase extends CodeInsightFixtureTestCase {

  public void doTest(@NonNls String folderName, LocalInspectionTool tool) {
    doTest(folderName, new LocalInspectionToolWrapper(tool));
  }

  public void doTest(@NonNls String folderName, @NotNull InspectionToolWrapper toolWrapper) {
    myFixture.testInspection(folderName, toolWrapper);
  }
}
