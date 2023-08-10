// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

public class PlainTextEnterActionTest extends AbstractEnterActionTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getPlatformTestDataPath();
  }

  public void testEnterInTextFile() throws Exception {
    doTest("txt");
  }

  public void testEnterWithSpacesAfterCaret() {
    doTextTest("txt", "abc <caret> ", "abc \n<caret> ");
  }

  public void testSCR2236() {
    doTextTest("txt", "//file/text/<caret>text/text", "//file/text/\n" + "<caret>text/text");
  }

  public void testUnmatchedBraceAtPlainTextFile() {
    // Inspired by IDEA-64060
    doTextTest(
      "txt",
      "   class Foo {<caret>",
      """
           class Foo {
           <caret>
           }\
        """
    );
  }

  public void testInJavaDocLikeStringOfPlainTextFile() {
    // Inspired by IDEA-65108
    doTextTest(
      "txt",
      "Here is a list of bullet points:\n" +
      "* Bullet one.  <caret>Bullet two.",
      """
        Here is a list of bullet points:
        * Bullet one. \s
        <caret>Bullet two."""
    );
  }

  public void testIndentCalculationWithTabsInIndent() {
    doTextTest("txt", "\t<caret>\tabc", "\t\n\t<caret>\tabc");
  }
}
