// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;

public abstract class CommentByLineTestBase extends LightPlatformCodeInsightTestCase {
  protected void doTest() {
    doTest(1, false);
  }

  public void doInvertedTest(int count) {
    doTest(count, false);
    doTest(count, true);
  }

  protected void doTest(int count, boolean invert) {
    String name = getTestName(true);
    String lang = name.split("[0-9_]+")[0];
    String suffix = name.substring(lang.length());
    suffix = StringUtil.trimStart(suffix, "_");
    configureByFile("/codeInsight/commentByLine/" + lang + (invert ? "/after" : "/before") + suffix + "." + lang);
    for (int i = 0; i < count; i++) {
      performAction();
    }
    checkResultByFile("/codeInsight/commentByLine/" + lang + (invert ? "/before" : "/after") + suffix + "." + lang);
  }

  protected static void performAction() {
    PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE);
  }
}
