package com.intellij.lang.properties;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LightIdeaTestCase;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 27, 2005
 * Time: 10:44:17 PM
 * To change this template use File | Settings | File Templates.
 */
public class PropertiesLexerTest extends LightIdeaTestCase {
  private static void doTest(String text, String[] expectedTokens) {
    Lexer lexer = new PropertiesLexer();
    lexer.start(text.toCharArray());
    int idx = 0;
    while (lexer.getTokenType() != null) {
      if (idx > expectedTokens.length) fail("Too many tokens");
      String tokenName = lexer.getTokenType().toString();
      assertEquals(expectedTokens[idx], tokenName);
      idx++;
      lexer.advance();
    }

    if (idx < expectedTokens.length) fail("Not enough tokens");
  }

  public void testSimple() throws Exception {
    doTest("xxx=yyy", new String[] {
               "Properties:KEY_CHARACTERS",
               "Properties:KEY_VALUE_SEPARATOR",
               "Properties:VALUE_CHARACTERS",
             });
  }

  public void testMulti() throws Exception {
    doTest("a=b\nx=y", new String[]{
               "Properties:KEY_CHARACTERS",
               "Properties:KEY_VALUE_SEPARATOR",
               "Properties:VALUE_CHARACTERS",
               "WHITE_SPACE",
               "Properties:KEY_CHARACTERS",
               "Properties:KEY_VALUE_SEPARATOR",
               "Properties:VALUE_CHARACTERS"
             });
  }

  public void testIncompleteProperty() throws Exception {
    doTest("a", new String[] {
      "Properties:KEY_CHARACTERS",
    });
  }
  public void testIncompleteProperty2() throws Exception {
    doTest("a.2=", new String[] {
      "Properties:KEY_CHARACTERS",
      "Properties:KEY_VALUE_SEPARATOR",
    });
  }
}
