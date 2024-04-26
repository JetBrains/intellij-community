// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions;

import com.intellij.codeInsight.CodeInsightBundle;

public class GrFlipCommaTest extends GrIntentionTestCase {
  public GrFlipCommaTest() {
    super(CodeInsightBundle.message("intention.name.flip"));
  }

  public void testFlipFirstAndMiddleParameters() {
    doTextTest("def m(String a<caret>, int b, boolean c) {}",
               "def m(int b<caret>, String a, boolean c) {}");
    doTextTest("def m(String a,<caret> int b, boolean c) {}",
               "def m(int b, String a, boolean c) {}");
  }

  public void testFlipMiddleAndLastParameters() {
    doTextTest("def m(String a, int b<caret>, boolean c) {}",
               "def m(String a, boolean c<caret>, int b) {}");
    doTextTest("def m(String a, int b,<caret> boolean c) {}",
               "def m(String a, boolean c, int b) {}");
  }

  public void testFlipFirstAndMiddleListElements() {
    doTextTest("[1<caret>, 2, 3]",
               "[2<caret>, 1, 3]");
    doTextTest("[1,<caret> 2, 3]",
               "[2, 1, 3]");
  }

  public void testFlipMiddleAndLastListElements() {
    doTextTest("[1, 2<caret>, 3]",
               "[1, 3<caret>, 2]");
    doTextTest("[1, 2,<caret> 3]",
               "[1, 3, 2]");
  }

  public void testFlipAfterNewline() {
    doTextTest("[1<caret>,\n 2]",
               "[2<caret>,\n 1]");
  }
}
