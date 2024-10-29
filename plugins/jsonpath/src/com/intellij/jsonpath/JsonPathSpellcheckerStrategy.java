// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jsonpath;

import com.intellij.jsonpath.psi.JsonPathId;
import com.intellij.jsonpath.psi.JsonPathIdSegment;
import com.intellij.jsonpath.psi.JsonPathStringLiteral;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class JsonPathSpellcheckerStrategy extends SpellcheckingStrategy implements DumbAware {

  private final Tokenizer<JsonPathStringLiteral> ourStringLiteralTokenizer = new Tokenizer<>() {
    @Override
    public void tokenize(@NotNull JsonPathStringLiteral element, @NotNull TokenConsumer consumer) {
      PlainTextSplitter textSplitter = PlainTextSplitter.getInstance();
      if (element.textContains('\\')) {
        List<Pair<TextRange, String>> fragments = element.getTextFragments();
        for (Pair<TextRange, String> fragment : fragments) {
          TextRange fragmentRange = fragment.getFirst();
          String escaped = fragment.getSecond();
          // Fragment without escaping, also not a broken escape sequence or a unicode code point
          if (escaped.length() == fragmentRange.getLength() && !escaped.startsWith("\\")) {
            consumer.consumeToken(element, escaped, false, fragmentRange.getStartOffset(), TextRange.allOf(escaped), textSplitter);
          }
        }
      }
      else {
        consumer.consumeToken(element, textSplitter);
      }
    }
  };

  private final Tokenizer<JsonPathId> idLiteralTokenizer = new Tokenizer<>() {
    @Override
    public void tokenize(@NotNull JsonPathId element, @NotNull TokenConsumer consumer) {
      PlainTextSplitter textSplitter = PlainTextSplitter.getInstance();
      consumer.consumeToken(element, textSplitter);
    }
  };

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof JsonPathStringLiteral) {
      return ourStringLiteralTokenizer;
    }
    if (element instanceof JsonPathId && element.getParent() instanceof JsonPathIdSegment) {
      return idLiteralTokenizer;
    }
    return super.getTokenizer(element);
  }
}
