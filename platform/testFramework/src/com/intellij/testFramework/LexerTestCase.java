// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.lang.TokenWrapper;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.RestartableLexer;
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
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class LexerTestCase extends UsefulTestCase {
  protected void doTest(@NotNull String text) {
    doTest(text, null);
  }

  protected void doTest(@NotNull String text, @Nullable String expected) {
    doTest(text, expected, createLexer());
  }

  protected void doTest(@NotNull String text, @Nullable String expected, @NotNull Lexer lexer) {
    String result = printTokens(lexer, text, 0);

    if (expected != null) {
      assertSameLines(expected, result);
    }
    else {
      assertSameLinesWithFile(getPathToTestDataFile(getExpectedFileExtension()), result);
    }
  }

  protected String printTokens(@NotNull Lexer lexer, @NotNull CharSequence text, int start) {
    return printTokens(text, start, lexer);
  }

  protected @NotNull String getPathToTestDataFile(@NotNull String extension) {
    return IdeaTestExecutionPolicy.getHomePathWithPolicy() + "/" + getDirPath() + "/" + getTestName(true) + extension;
  }

  protected @NotNull String getExpectedFileExtension() {
    return ".txt";
  }

  protected void checkZeroState(@NotNull String text, TokenSet tokenTypes) {
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

  protected String printTokens(@NotNull String text, int start) {
    return printTokens(text, start, createLexer());
  }

  protected void checkCorrectRestart(@NotNull String text) {
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
      int state = auxLexer.getState();
      if (state == 0 || (auxLexer instanceof RestartableLexer && ((RestartableLexer)auxLexer).isRestartableState(state))) {
        int tokenStart = auxLexer.getTokenStart();
        List<Trinity<IElementType, Integer, Integer>> expectedTokens = allTokens.subList(index, allTokens.size());
        List<Trinity<IElementType, Integer, Integer>> restartedTokens = tokenize(text, tokenStart, state, mainLexer);
        assertEquals(
          "Restarting impossible from offset " + tokenStart + " - " + auxLexer.getTokenText() + "\n" +
          "All tokens <type, offset, lexer state>: " + allTokens + "\n",
          expectedTokens.stream().map(Objects::toString).collect(Collectors.joining("\n")),
          restartedTokens.stream().map(Objects::toString).collect(Collectors.joining("\n"))
        );
      }
      index++;
      auxLexer.advance();
    }
  }

  private static @NotNull List<Trinity<IElementType, Integer, Integer>> tokenize(@NotNull String text,
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

  public static String printTokens(@NotNull CharSequence text, int start, @NotNull Lexer lexer) {
    lexer.start(text, start, text.length());
    StringBuilder result = new StringBuilder();
    IElementType tokenType;
    while ((tokenType = lexer.getTokenType()) != null) {
      result.append(printSingleToken(text, tokenType, lexer.getTokenStart(), lexer.getTokenEnd()));
      lexer.advance();
    }
    return result.toString();
  }

  public static @NotNull String printTokens(@NotNull HighlighterIterator iterator) {
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

  public static String printSingleToken(@NotNull CharSequence fileText, @NotNull IElementType tokenType, int start, int end) {
    return tokenType + " ('" + getTokenText(tokenType, fileText, start, end) + "')\n";
  }

  protected void doFileTest(@NotNull String fileExt) {
    doTest(loadTestDataFile("." + fileExt));
  }

  protected @NotNull String loadTestDataFile(String fileExt) {
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

  private static @NotNull String getTokenText(IElementType tokenType, CharSequence sequence, int start, int end) {
    return tokenType instanceof TokenWrapper
           ? ((TokenWrapper)tokenType).getText()
           : StringUtil.replace(sequence.subSequence(start, end).toString(), "\n", "\\n");
  }

  protected abstract @NotNull Lexer createLexer();

  protected abstract @NotNull String getDirPath();
}