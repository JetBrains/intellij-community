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
      if (idx >= expectedTokens.length) fail("Too many tokens");
      String tokenName = lexer.getTokenType().toString();
      String expectedTokenType = expectedTokens[idx++];
      String expectedTokenText = expectedTokens[idx++];
      assertEquals(expectedTokenType, tokenName);
      String tokenText = new String(lexer.getBuffer(), lexer.getTokenStart(), lexer.getTokenEnd()-lexer.getTokenStart());
      assertEquals(expectedTokenText, tokenText);
      lexer.advance();
    }

    if (idx < expectedTokens.length) fail("Not enough tokens");
  }

  public void testSimple() throws Exception {
    doTest("xxx=yyy", new String[] {
               "Properties:KEY_CHARACTERS", "xxx",
               "Properties:KEY_VALUE_SEPARATOR", "=",
               "Properties:VALUE_CHARACTERS", "yyy",
             });
  }

  public void testMulti() throws Exception {
    doTest("a  b\n \nx\ty", new String[]{
               "Properties:KEY_CHARACTERS", "a",
               "Properties:KEY_VALUE_SEPARATOR", "  ",
               "Properties:VALUE_CHARACTERS",    "b",
               "WHITE_SPACE",                    "\n \n",
               "Properties:KEY_CHARACTERS",      "x",
               "Properties:KEY_VALUE_SEPARATOR", "\t",
               "Properties:VALUE_CHARACTERS",    "y"
             });
  }

  public void testIncompleteProperty() throws Exception {
    doTest("a", new String[] {
      "Properties:KEY_CHARACTERS", "a"
    });
  }
  public void testIncompleteProperty2() throws Exception {
    doTest("a.2=", new String[] {
      "Properties:KEY_CHARACTERS", "a.2",
      "Properties:KEY_VALUE_SEPARATOR", "="
    });
  }
  public void testEscaping() throws Exception {
    doTest("sdlfkjsd\\l\\\\\\:\\=gk   =   s\\nsssd", new String[] {
               "Properties:KEY_CHARACTERS", "sdlfkjsd\\l\\\\\\:\\=gk",
               "Properties:KEY_VALUE_SEPARATOR", "   =   ",
               "Properties:VALUE_CHARACTERS", "s\\nsssd"
             });
  }
}
