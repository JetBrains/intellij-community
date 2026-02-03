// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

public class CustomFileTypeCommentByLineTest extends CommentByLineTestBase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath();
  }

  public void testPl1() {
    doTest();
  }
  public void testPl2() {
    doTest();
  }
}
