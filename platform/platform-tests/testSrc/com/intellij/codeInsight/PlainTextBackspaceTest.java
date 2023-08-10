// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PlainTextBackspaceTest extends LightPlatformCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath();
  }

  public void testDifferentBrackets() { doTest(); }

  private void doTest() {
    @NonNls String path = "/codeInsight/backspace/";

    configureByFile(path + getTestName(false) + ".txt");
    backspace();
    checkResultByFile(path + getTestName(false) + "_after.txt");
  }
}
