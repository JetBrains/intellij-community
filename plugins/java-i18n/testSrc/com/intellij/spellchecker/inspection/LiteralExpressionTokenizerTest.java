// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.inspection;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.LiteralExpressionTokenizer;
import com.intellij.spellchecker.inspections.Splitter;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.Consumer;

import java.util.ArrayList;
import java.util.List;


public class LiteralExpressionTokenizerTest extends BasePlatformTestCase {
  private static class TokenCollector extends TokenConsumer implements Consumer<TextRange> {
    private final List<String> myTokenTexts = new ArrayList<>();
    private String myText;

    @Override
    public void consumeToken(PsiElement element, String text, boolean useRename, int offset, TextRange rangeToCheck, Splitter splitter) {
      myText = text;
      splitter.split(myText, rangeToCheck, this);
    }

    public List<String> getTokenTexts() {
      return myTokenTexts;
    }

    @Override
    public void consume(TextRange range) {
      myTokenTexts.add(range.substring(myText));
    }
  }

  public void testEscapeSequences() {
    doTest("hello\\nworld", "hello", "world");
  }

  public void testEscapeSequences2() {
    doTest("\\nhello\\nworld\\n", "hello", "world");
  }

  private static void doTest(final String text, final String... expected) {
    TokenCollector collector = new TokenCollector();
    LiteralExpressionTokenizer.processTextWithEscapeSequences(null, text, collector);
    assertOrderedEquals(collector.getTokenTexts(), expected);
  }
}
