// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchTypedHandlerTest extends BasePlatformTestCase {

  public void testNoSelection() {
    doTest("<caret>aaa\nbbb\n\n", "$<caret>$aaa\nbbb\n\n");
  }

  public void testTypeOver() {
    doTest("$X<caret>$", "$X$<caret>");
  }

  public void testMultipleCarets() {
    doTest("$c<caret>$ <caret> ", "$c$<caret> $<caret>$ ");
  }

  public void testSurroundSelection() {
    doTest("<selection>bla<caret></selection> no class",
           "$<selection>bla<caret></selection>$ no class");
  }

  public void testCombined() {
    doTest("$x<caret>$ y<caret>z <selection>abc<caret></selection>",
           "$x$<caret> y$<caret>$z $<selection>abc<caret></selection>$");
  }

  public void testOneDollar() {
    doTest("asdf<caret>$asdf", "asdf$<caret>$asdf");
  }

  public void testOddDollar1() {
    doTest("<caret>ba$", "$<caret>ba$");
  }

  public void testOddDollar2() {
    doTest("$ietske<caret>", "$ietske$<caret>");
  }

  public void testTabs() {
    doTest("\t\t<caret>", "\t\t$<caret>$");
  }

  private void doTest(@NotNull String before, @NotNull String after) {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, before);
    final Editor editor = myFixture.getEditor();
    editor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, new SearchConfiguration());
    myFixture.type("$");
    myFixture.checkResult(after);
  }
}