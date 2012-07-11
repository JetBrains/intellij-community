package com.jetbrains.gettext;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.UsefulTestCase;

import java.io.File;
import java.io.IOException;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextLexerTest extends UsefulTestCase {

  private static void doFileLexerTest(Lexer lexer, String testText, String expected) {
    lexer.start(testText);
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
    assertSameLinesWithFile(expected, result);
  }

  private static String getTokenText(Lexer lexer) {
    return lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
  }

  private static void doTest(String fileName) throws IOException {
    final Lexer lexer = new GetTextLexer();
    final String testText = getFileText(GetTextUtils.getFullSourcePath(fileName));
    final String expected = GetTextUtils.getFullLexerPath(fileName);
    doFileLexerTest(lexer, testText, expected);
  }

  public static String getFileText(final String filePath) {
    try {
      return FileUtil.loadFile(new File(filePath));
    }
    catch (IOException e) {
      System.out.println(filePath);
      throw new RuntimeException(e);
    }
  }

  public void testLexer() throws Throwable {
    doTest("command_format");
  }

  public void testAllFiles() throws Throwable {
    for (final String file : GetTextUtils.getAllTestedFiles()) {
      doTest(file);
    }
  }
}


