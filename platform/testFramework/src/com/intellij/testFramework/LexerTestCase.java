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
  private static final String defaultExpectedOutputExt = "txt";
  private static final TokenTypePrinter defaultTokenTypePrinter = (s, e, tokenType, tokenSequence) ->
    tokenType + " ('" + getTokenText(tokenType, tokenSequence.toString()) + "')\n";

  protected void doTest(@NonNls String text) {
    doTest(text, null);
  }

  protected void doTest(@NonNls String text, @Nullable String expected) {
    doTest(text, expected, createLexer());
  }

  protected void doTest(@NonNls String text, @Nullable String expected, @NotNull Lexer lexer) {
    String result = customPrintTokens(text, 0, lexer);

    if (expected != null) {
      assertSameLines(expected, result);
    }
    else {
      assertSameLinesWithFile(getFilePathWithoutExt() + "." + getExpectedOutputExt(), result, shouldTrim());
    }
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
    String allTokens = customPrintTokens(text, 0, mainLexer);

    Lexer auxLexer = createLexer();
    auxLexer.start(text);
    while (true) {
      IElementType type = auxLexer.getTokenType();
      if (type == null) {
        break;
      }
      if (auxLexer.getState() == 0) {
        int tokenStart = auxLexer.getTokenStart();
        String subTokens = customPrintTokens(text, tokenStart, mainLexer);
        if (!allTokens.endsWith(subTokens)) {
          assertEquals("Restarting impossible from offset " + tokenStart + "; lexer state should not return 0 at this point", allTokens, subTokens);
        }
      }
      auxLexer.advance();
    }
  }

  protected String customPrintTokens(String text, int start, Lexer lexer) {
    return printTokens(text, start, lexer);
  }

  public static String printTokens(CharSequence text, int start, Lexer lexer) {
    return printTokens(text, start, lexer, defaultTokenTypePrinter);
  }

  public static String printTokens(CharSequence text, int start, Lexer lexer, TokenTypePrinter printer) {
    lexer.start(text, start, text.length());
    StringBuilder result = new StringBuilder();
    IElementType token;
    while ((token = lexer.getTokenType()) != null) {
      CharSequence sequence = lexer.getTokenSequence();
      String printTokeType = printer.print(lexer.getTokenStart(), lexer.getTokenEnd(), token, sequence);
      result.append(printTokeType);
      lexer.advance();
    }
    return result.toString();
  }

  @Deprecated
  protected void doFileTest(@NonNls String fileExt) {
    String fileName = getFilePathWithoutExt() + "." + fileExt;
    String text = "";
    try {
      String fileText = FileUtil.loadFile(new File(fileName));
      text = shouldTrim() ? fileText.trim() : fileText;
      text = !shouldConvertLineSeparators() ? text : StringUtil.convertLineSeparators(text);
    } catch (IOException e) {
      fail("can't load file " + fileName + ": " + e.getMessage());
    }
    doTest(text);
  }

  protected void doFileTest() {
    doFileTest(getTestFileExt());
  }

  protected boolean shouldTrim() {
    return true;
  }

  protected boolean shouldConvertLineSeparators() {
    return true;
  }

  private static String getTokenText(IElementType tokenType, String tokenString) {
    if (tokenType instanceof TokenWrapper) {
      return ((TokenWrapper) tokenType).getValue();
    }
    return StringUtil.replace(tokenString, "\n", "\\n");
  }

  protected abstract Lexer createLexer();

  protected abstract String getDirPath();

  protected String getTestFileExt() {
    return "testExt";
  }

  protected String getExpectedOutputExt() {
    return defaultExpectedOutputExt;
  }

  @NotNull
  protected String getFilePathWithoutExt() {
    return FileUtil.join(PathManager.getHomePath(), getDirPath(), getTestName(true));
  }

  public interface TokenTypePrinter {
    String print(int tokenStart, int tokenEnd, IElementType tokenType, CharSequence tokenSequence);
  }
}