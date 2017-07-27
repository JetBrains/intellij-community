/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testFramework;

import com.intellij.lang.TokenWrapper;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author peter
 */
public abstract class LexerTestCase extends UsefulTestCase {
  protected void doTest(@NonNls String text) {
    doTest(text, null);
  }

  protected void doTest(@NonNls String text, @Nullable String expected) {
    doTest(text, expected, createLexer());
  }

  protected void doTest(@NonNls String text, @Nullable String expected, @NotNull Lexer lexer) {
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
    return PathManager.getHomePath() + "/" + getDirPath() + "/" + getTestName(true) + extension;
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

  public static String printSingleToken(CharSequence fileText, IElementType tokenType, int start, int end) {
    return tokenType + " ('" + getTokenText(tokenType, fileText, start, end) + "')\n";
  }

  protected void doFileTest(@NonNls String fileExt) {
    doTest(loadTestDataFile("." + fileExt));
  }

  @NotNull
  protected String loadTestDataFile(@NonNls String fileExt) {
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
    if (tokenType instanceof TokenWrapper) {
      return ((TokenWrapper)tokenType).getValue();
    }

    return StringUtil.replace(sequence.subSequence(start, end).toString(), "\n", "\\n");
  }

  protected abstract Lexer createLexer();

  protected abstract String getDirPath();
}