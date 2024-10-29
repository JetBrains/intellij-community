// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.spellchecker;

import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.tokenizer.EscapeSequenceTokenizer;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

public class GroovySpellcheckingStrategy extends SpellcheckingStrategy implements DumbAware {
  private final GrDocCommentTokenizer myDocCommentTokenizer = new GrDocCommentTokenizer();
  private final Tokenizer<PsiElement> myStringTokenizer = new EscapeSequenceTokenizer<>() {
    @Override
    public void tokenize(@NotNull PsiElement literal, @NotNull TokenConsumer consumer) {
      String text = GrStringUtil.removeQuotes(literal.getText());
      if (!text.contains("\\")) {
        consumer.consumeToken(literal, PlainTextSplitter.getInstance());
      }
      else {
        StringBuilder unescapedText = new StringBuilder();
        int[] offsets = new int[text.length() + 1];
        GrStringUtil.parseStringCharacters(text, unescapedText, offsets);
        processTextWithOffsets(literal, consumer, unescapedText, offsets, GrStringUtil.getStartQuote(literal.getText()).length());
      }
    }
  };

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    if (TokenSets.STRING_LITERAL_SET.contains(element.getNode().getElementType())) {
      return myStringTokenizer;
    }
    if (element instanceof GrNamedElement) {
      final PsiElement name = ((GrNamedElement)element).getNameIdentifierGroovy();
      if (TokenSets.STRING_LITERAL_SET.contains(name.getNode().getElementType())) {
        return EMPTY_TOKENIZER;
      }
    }
    if (element instanceof PsiDocComment) return myDocCommentTokenizer;
    //if (element instanceof GrLiteralImpl && ((GrLiteralImpl)element).isStringLiteral()) return myStringTokenizer;
    return super.getTokenizer(element);
  }
}
