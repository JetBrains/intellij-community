// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.lang.TokenWrapper;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.util.Trinity;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.testFramework.fixtures.IdeaTestExecutionPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public abstract class LexerTestCase extends UsefulTestCase {
  protected void doTest(String text) {
    doTest(text, null);
  }

  protected void doTest(String text, @Nullable String expected) {
    doTest(text, expected, createLexer());
  }

  protected void doTest(String text, @Nullable String expected, @NotNull Lexer lexer) {
    String result = printTokens(text, 0, lexer);

    if (expected != null) {
      assertSameLines(expected, result);
    }
    else {
      assertSameLinesWithFile(getPathToTestDataFile(getExpectedFileExtension()), result);
    }
  }

  @NotNull
  protected String getPathToTestDataFile(String extension) {
    return IdeaTestExecutionPolicy.getHomePathWithPolicy() + "/" + getDirPath() + "/" + getTestName(true) + extension;
  }

  @NotNull
  protected String getExpectedFileExtension() {
    return ".txt";
  }

  protected void checkZeroState(String text, TokenSet tokenTypes) {
    Lexer lexer = createLexer();
    lexer.start(text);

    while (true) {
      IElementType type = lexer.getTokenType();
      if (type == null) {
        break;
      }
      if (tokenTypes.contains(type) && lexer.getState() != 0) {
        fail("Non-zero lexer state on token \"" + lexer.getTokenText() + "\" (" + type + ") at " + lexer.getTokenStart());
      }
      lexer.advance();
    }
  }

  protected void checkCorrectRestart(String text) {
    Lexer mainLexer = createLexer();
    String allTokens = printTokens(text, 0, mainLexer);

    Lexer auxLexer = createLexer();
    auxLexer.start(text);
    while (true) {
      IElementType type = auxLexer.getTokenType();
      if (type == null) {
        break;
      }
      if (auxLexer.getState() == 0) {
        int tokenStart = auxLexer.getTokenStart();
        String subTokens = printTokens(text, tokenStart, mainLexer);
        if (!allTokens.endsWith(subTokens)) {
          assertEquals("Restarting impossible from offset " + tokenStart + "; lexer state should not return 0 at this point", allTokens, subTokens);
        }
      }
      auxLexer.advance();
    }
  }

  protected String printTokens(String text, int start) {
    return printTokens(text, start, createLexer());
  }

  protected void checkCorrectRestartOnEveryToken(@NotNull String text) {
    Lexer mainLexer = createLexer();
    List<Trinity<IElementType, Integer, Integer>> allTokens = tokenize(text, 0, 0, mainLexer);
    Lexer auxLexer = createLexer();
    auxLexer.start(text);
    int index = 0;
    while (true) {
      IElementType type = auxLexer.getTokenType();
      if (type == null) {
        break;
      }
      List<Trinity<IElementType, Integer, Integer>> subTokens = tokenize(text, auxLexer.getTokenStart(), auxLexer.getState(), mainLexer);
      if (!allTokens.subList(index++, allTokens.size()).equals(subTokens)) {
        assertEquals("Restarting impossible from offset " + auxLexer.getTokenStart() + " - " + auxLexer.getTokenText() + "\n" +
                     "All tokens <type, offset, lexer state>: " + allTokens + "\n",
                     allTokens.subList(index - 1, allTokens.size()),
                     subTokens);
      }
      auxLexer.advance();
    }
  }

  @NotNull
  private static List<Trinity<IElementType, Integer, Integer>> tokenize(@NotNull String text,
                                                                        int start,
                                                                        int state,
                                                                        @NotNull Lexer lexer) {
    List<Trinity<IElementType, Integer, Integer>> allTokens = new ArrayList<>();
    try {
      lexer.start(text, start, text.length(), state);
    }
    catch (Throwable t) {
      LOG.error("Restarting impossible from offset " + start, t);
      throw new RuntimeException(t);
    }
    while (lexer.getTokenType() != null) {
      allTokens.add(Trinity.create(lexer.getTokenType(), lexer.getTokenStart(), lexer.getState()));
      lexer.advance();
    }
    return allTokens;
  }

  public static String printTokens(CharSequence text, int start, Lexer lexer) {
    lexer.start(text, start, text.length());
    StringBuilder result = new StringBuilder();
    IElementType tokenType;
    while ((tokenType = lexer.getTokenType()) != null) {
      result.append(printSingleToken(text, tokenType, lexer.getTokenStart(), lexer.getTokenEnd()));
      lexer.advance();
    }
    return result.toString();
  }

  @NotNull
  public static String printTokens(@NotNull HighlighterIterator iterator) {
    CharSequence text = iterator.getDocument().getCharsSequence();
    StringBuilder result = new StringBuilder();
    IElementType tokenType;
    while (!iterator.atEnd()) {
      tokenType = iterator.getTokenType();
      result.append(printSingleToken(text, tokenType, iterator.getStart(), iterator.getEnd()));
      iterator.advance();
    }
    return result.toString();
  }

  public static String printSingleToken(CharSequence fileText, IElementType tokenType, int start, int end) {
    return tokenType + " ('" + getTokenText(tokenType, fileText, start, end) + "')\n";
  }

  protected void doFileTest(String fileExt) {
    doTest(loadTestDataFile("." + fileExt));
  }

  @NotNull
  protected String loadTestDataFile(String fileExt) {
    String fileName = getPathToTestDataFile(fileExt);
    String text = "";
    try {
      String fileText = FileUtil.loadFile(new File(fileName));
      text = StringUtil.convertLineSeparators(shouldTrim() ? fileText.trim() : fileText);
    }
    catch (IOException e) {
      fail("can't load file " + fileName + ": " + e.getMessage());
    }
    return text;
  }

  protected boolean shouldTrim() {
    return true;
  }

  @NotNull
  private static String getTokenText(IElementType tokenType, CharSequence sequence, int start, int end) {
    return tokenType instanceof TokenWrapper
           ? ((TokenWrapper)tokenType).getValue()
           : StringUtil.replace(sequence.subSequence(start, end).toString(), "\n", "\\n");
  }

  protected abstract Lexer createLexer();

  protected abstract String getDirPath();
}