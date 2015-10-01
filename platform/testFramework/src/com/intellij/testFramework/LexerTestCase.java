/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
      assertSameLinesWithFile(PathManager.getHomePath() + "/" + getDirPath() + "/" + getTestName(true) + ".txt", result);
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
    String result = "";
    while (true) {
      IElementType tokenType = lexer.getTokenType();
      if (tokenType == null) {
        break;
      }
      String tokenText = getTokenText(lexer);
      String tokenTypeName = tokenType.toString();
      String line = tokenTypeName + " ('" + tokenText + "')\n";
      result += line;
      lexer.advance();
    }
    return result;
  }

  protected void doFileTest(@NonNls String fileExt) {
    String fileName = PathManager.getHomePath() + "/" + getDirPath() + "/" + getTestName(true) + "." + fileExt;
    String text = "";
    try {
      String fileText = FileUtil.loadFile(new File(fileName));
      text = StringUtil.convertLineSeparators(shouldTrim() ? fileText.trim() : fileText);
    }
    catch (IOException e) {
      fail("can't load file " + fileName + ": " + e.getMessage());
    }
    doTest(text);
  }

  protected boolean shouldTrim() {
    return true;
  }

  private static String getTokenText(Lexer lexer) {
    final IElementType tokenType = lexer.getTokenType();
    if (tokenType instanceof TokenWrapper) {
      return ((TokenWrapper)tokenType).getValue();
    }

    String text = lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
    text = StringUtil.replace(text, "\n", "\\n");
    return text;
  }

  protected abstract Lexer createLexer();

  protected abstract String getDirPath();
}
