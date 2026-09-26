// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.lang.TokenWrapper;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.lexer.RestartableLexer;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class LexerTestCase extends UsefulTestCase {
  protected void doTest(@NotNull String text) {
    doTest(text, null);
  }

  protected void doTest(@NotNull String text, @Nullable String expected) {
    doTest(text, expected, createLexer());
    checkCorrectRestart(text);
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

  protected void checkZeroState(@NotNull String text, @NotNull TokenSet tokenTypes) {
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

  /**
   * Verifies that the lexer produces the same token sequence when restarted from any position
   * where {@link Lexer#getState()} returns zero or {@link RestartableLexer#isRestartableState(int)} returns {@code true}.
   *
   * <p>For every such position the lexer is restarted via {@link Lexer#start(CharSequence, int, int, int)}
   * with the recorded offset and state, and the resulting tokens are compared against the tail of the
   * initial full-text tokenization.
   */
  protected void checkCorrectRestart(@NotNull String text) {
    Lexer mainLexer = createLexer();
    List<TokenState> allTokens = tokenize(text, 0, 0, mainLexer);
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
        List<TokenState> expectedTokens = allTokens.subList(index, allTokens.size());
        List<TokenState> restartedTokens = tokenize(text, tokenStart, state, mainLexer);
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

  /**
   * Verifies that the lexer produces the same token sequence when restored to any position
   * captured by {@link Lexer#getCurrentPosition()}.
   *
   * <p>Unlike {@link #checkCorrectRestart(String)}, which restarts the lexer via
   * {@link Lexer#start(CharSequence, int, int, int)} using only the integer offset and state
   * (and therefore can only test positions where the integer state is sufficient for a faithful restart),
   * this method uses {@link Lexer#restore(LexerPosition)} and tests <em>every</em> token position.
   *
   * <p>This is possible because {@link LexerPosition} implementations may carry additional internal
   * state beyond the integer returned by {@link Lexer#getState()} (e.g. delegate positions,
   * lookahead caches, or embedment info), allowing {@code restore()} to reconstruct the full
   * lexer state at any point.
   */
  protected void checkCorrectRestartUsingPosition(@NotNull String text) {
    Lexer mainLexer = createLexer();
    List<TokenState> allTokens = tokenize(text, 0, 0, mainLexer);
    List<LexerPosition> allPositions = buildPositions(text, 0, mainLexer);
    for (int i = 0; i < allPositions.size(); i++) {
      LexerPosition position = allPositions.get(i);
      List<TokenState> expectedTokens = allTokens.subList(i, allTokens.size());
      List<TokenState> restartedTokens = tokenize(position, mainLexer);
      mainLexer.restore(position);
      assertEquals(
        "Restarting using position impossible from offset " + position.getOffset() + " - " + mainLexer.getTokenText() + "\n" +
        "All tokens <type, offset, lexer state>: " + allTokens + "\n",
        expectedTokens.stream().map(Objects::toString).collect(Collectors.joining("\n")),
        restartedTokens.stream().map(Objects::toString).collect(Collectors.joining("\n"))
      );
    }
  }

  private static @NotNull List<TokenState> tokenize(@NotNull String text,
                                                    int start,
                                                    int state,
                                                    @NotNull Lexer lexer) {
    List<TokenState> allTokens = new ArrayList<>();
    try {
      lexer.start(text, start, text.length(), state);
    }
    catch (Throwable t) {
      LOG.error("Restarting impossible from offset " + start, t);
      throw new RuntimeException(t);
    }
    while (lexer.getTokenType() != null) {
      allTokens.add(new TokenState(lexer.getTokenType(), lexer.getTokenStart(), lexer.getState()));
      lexer.advance();
    }
    return allTokens;
  }


  private static @NotNull List<LexerPosition> buildPositions(@NotNull String text,
                                                             int start,
                                                             @NotNull Lexer lexer) {
    List<LexerPosition> result = new ArrayList<>();
    try {
      lexer.start(text, start, text.length());
    }
    catch (Throwable t) {
      LOG.error("Restarting impossible from offset " + start, t);
      throw new RuntimeException(t);
    }
    while (lexer.getTokenType() != null) {
      result.add(lexer.getCurrentPosition());
      lexer.advance();
    }
    return result;
  }

  private static @NotNull List<TokenState> tokenize(@NotNull LexerPosition position,
                                                    @NotNull Lexer lexer) {
    List<TokenState> allTokens = new ArrayList<>();
    try {
      lexer.restore(position);
    }
    catch (Throwable t) {
      LOG.error("Restoring location impossible from offset " + position.getOffset(), t);
      throw new RuntimeException(t);
    }
    while (lexer.getTokenType() != null) {
      allTokens.add(new TokenState(lexer.getTokenType(), lexer.getTokenStart(), lexer.getState()));
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

  /**
   * If you need to customize the lexer creation, see {@link WithLexerFactory}.
   */
  protected abstract @NotNull Lexer createLexer();

  protected abstract @NotNull String getDirPath();

  private record TokenState(@NotNull IElementType type, int offset, int state) {
  }

  /**
   * Utility class for lexer testing with a custom lexer factory.
   */
  public abstract static class WithLexerFactory extends LexerTestCase {
    private @Nullable Supplier<? extends @NotNull Lexer> myLexerFactory = null;

    @Override
    protected void tearDown() throws Exception {
      myLexerFactory = null;
      super.tearDown();
    }

    public final void setLexerFactory(@NotNull Supplier<? extends @NotNull Lexer> lexerFactory) {
      myLexerFactory = lexerFactory;
    }

    @Override
    protected final @NotNull Lexer createLexer() {
      if (myLexerFactory == null) {
        throw new AssertionError("Lexer factory is not set, you must call `setLexerFactory(...)` before calling doTest(...)");
      }

      return myLexerFactory.get();
    }
  }
}