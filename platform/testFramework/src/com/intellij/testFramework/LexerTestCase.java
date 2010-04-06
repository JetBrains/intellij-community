/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.io.IOException;

/**
 * @author peter
 */
public abstract class LexerTestCase extends UsefulTestCase {

  protected void doTest(@NonNls String text) {
    Lexer lexer = createLexer();
    lexer.start(text);
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
    assertSameLinesWithFile(PathManager.getHomePath() + "/" + getDirPath() + "/" + getTestName(true) + ".txt", result);
  }

  protected void doFileTest(@NonNls String fileExt) {
    String fileName = PathManager.getHomePath() + "/" + getDirPath() + "/" + getTestName(true) + "." + fileExt;
    String text = "";
    try {
      text = new String(FileUtil.loadFileText(new File(fileName))).trim();
    }
    catch (IOException e) {
      fail("can't load file " + fileName + ": " + e.getMessage());
    }
    doTest(text);
  }

  private static String getTokenText(Lexer lexer) {
    String text = lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
    text = StringUtil.replace(text, "\n", "\\n");
    return text;
  }

  protected abstract Lexer createLexer();

  protected abstract String getDirPath();
}
