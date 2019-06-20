// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchBackspaceHandlerTest extends BasePlatformTestCase {

  public void testRemoveDollar() {
    doTest("$<caret>$", "");
  }

  public void testDollarAtEnd() {
    doTest("fistfullof$<caret>", "fistfullof<caret>");
  }

  public void testOddDollar() {
    doTest("$hello$<caret>$", "$hello<caret>$");
  }

  private void doTest(@NotNull String before, @NotNull String after) {
    myFixture.configureByText(FileTypes.PLAIN_TEXT, before);
    final Editor editor = myFixture.getEditor();
    editor.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, new SearchConfiguration());
    myFixture.type("\b");
    myFixture.checkResult(after);
  }
}