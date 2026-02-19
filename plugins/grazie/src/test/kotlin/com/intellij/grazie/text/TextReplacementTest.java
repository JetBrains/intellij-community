package com.intellij.grazie.text;

import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieReplaceTypoQuickFix;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import kotlin.Pair;

import java.util.List;

public class TextReplacementTest extends BasePlatformTestCase {

  public void testHonorWordBounds() {
    PsiFile file = myFixture.configureByText("a.md", "at on [First model](url)");
    TextContent text = TextExtractor.findTextAt(file, 0, TextContent.TextDomain.ALL);
    List<Pair<SmartPsiFileRange, String>> replacements =
      GrazieReplaceTypoQuickFix.toFileReplacements(TextRange.from(text.toString().indexOf("on"), "on First".length()), "the first", text);
    WriteCommandAction.runWriteCommandAction(getProject(), () ->
      GrazieReplaceTypoQuickFix.applyReplacements(myFixture.getEditor().getDocument(), replacements));
    myFixture.checkResult("at [the first model](url)"); // the result could be different, but the markup should still be preserved
  }
}
