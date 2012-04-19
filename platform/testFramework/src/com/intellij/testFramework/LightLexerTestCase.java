package com.intellij.testFramework;

import com.intellij.FileSetTestCase;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;

/**
 * User: Andrey.Vokin
 * Date: 4/19/12
 */
public abstract class LightLexerTestCase extends FileSetTestCase {
  public LightLexerTestCase(String path) {
    super(path);
  }

  protected abstract Lexer getLexer();

  @Override
  public String transform(String testName, String[] data) throws Exception {
    final StringBuilder output = new StringBuilder();
    Lexer lexer = getLexer();
    final String text = data[0].replaceAll("$(\\n+)", "");
    lexer.start(text);
    while (lexer.getTokenType() != null) {
      final int s = lexer.getTokenStart();
      final int e = lexer.getTokenEnd();
      final IElementType tokenType = lexer.getTokenType();
      final String str = tokenType + ": [" + s + ", " + e + "], {" + text.substring(s, e) + "}\n";
      output.append(str);

      lexer.advance();
    }
    return output.toString();
  }
}
