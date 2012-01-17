package com.intellij.ide.highlighter.custom;

import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import junit.framework.TestCase;

/**
 * @author dsl
 */
public abstract class CustomFileTypeLexerTestBase extends TestCase {
  protected Lexer lexer;

  @Override
  protected void setUp() throws Exception {
    lexer = new CustomFileTypeLexer(createSyntaxTable());
  }

  protected abstract SyntaxTable createSyntaxTable();

  protected void checkTypesAndTokens(String sampleCode, IElementType[] types, String[] matches) {
    lexer.start(sampleCode);
    assertEquals(types.length, matches.length);

    for (int i = 0; i < types.length; i++) {
      assertEquals("Failed at index=" + i, types[i], lexer.getTokenType());
      assertEquals("Failed at index=" + i, matches[i], sampleCode.substring(lexer.getTokenStart(), lexer.getTokenEnd()));
      lexer.advance();
    }
  }

  protected void checkSameText(String sampleCode) {
    lexer.start(sampleCode);
    StringBuffer sb = new StringBuffer();
    String result;
    while (lexer.getTokenType() != null) {
      result = sampleCode.substring(lexer.getTokenStart(), lexer.getTokenEnd());
      sb.append(result);
      lexer.advance();
    }

    assertEquals("Text created by lexer's output does not match the original text", sampleCode, sb.toString());
  }

  public void testNothing() throws Exception {
    //TODO[dsl] Testcase fails if there's no any testcase. So I've added an empty one.
  }
}
