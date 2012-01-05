package com.intellij.ide.highlighter.custom;

import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;

/**
 * @author dsl
 */
public class PropertiesFileTest extends CustomFileTypeLexerTestBase {

  @Override
  protected void setUp() throws Exception {
    lexer = new CustomFileTypeLexer(createSyntaxTable());
  }

  @Override
  protected SyntaxTable createSyntaxTable() {
    SyntaxTable table = new SyntaxTable();

    table.setLineComment("#");
    table.setIgnoreCase(true);

    table.addKeyword1("value");
    table.addKeyword2("Value");
    table.setNumPostfixChars("LGH");
    return table;
  }

  public void testSimple() {
    String sampleFile =
            "# Comment\n" +
            "x.1.a=12.2L\n" +
            "   y.2.b=13.4 # comment\n" +
            "VALUE value VaLuE Value1 17.00h 11.0k";
    checkSameText(sampleFile);
    IElementType[] types = {
      CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.WHITESPACE,

      CustomHighlighterTokenType.IDENTIFIER, CustomHighlighterTokenType.PUNCTUATION,
      CustomHighlighterTokenType.NUMBER, CustomHighlighterTokenType.PUNCTUATION,
      CustomHighlighterTokenType.IDENTIFIER, CustomHighlighterTokenType.CHARACTER,
      CustomHighlighterTokenType.NUMBER, CustomHighlighterTokenType.WHITESPACE,

      CustomHighlighterTokenType.IDENTIFIER, CustomHighlighterTokenType.PUNCTUATION,
      CustomHighlighterTokenType.NUMBER, CustomHighlighterTokenType.PUNCTUATION,
      CustomHighlighterTokenType.IDENTIFIER, CustomHighlighterTokenType.CHARACTER,
      CustomHighlighterTokenType.NUMBER, CustomHighlighterTokenType.WHITESPACE,
      CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.WHITESPACE,

      CustomHighlighterTokenType.KEYWORD_1, CustomHighlighterTokenType.WHITESPACE,
      CustomHighlighterTokenType.KEYWORD_1, CustomHighlighterTokenType.WHITESPACE,
      CustomHighlighterTokenType.KEYWORD_1, CustomHighlighterTokenType.WHITESPACE,
      CustomHighlighterTokenType.IDENTIFIER, CustomHighlighterTokenType.WHITESPACE,
      CustomHighlighterTokenType.NUMBER, CustomHighlighterTokenType.WHITESPACE,
      CustomHighlighterTokenType.NUMBER, CustomHighlighterTokenType.PUNCTUATION,
      CustomHighlighterTokenType.NUMBER,
      CustomHighlighterTokenType.IDENTIFIER
    };

    String[] matches = {
      "# Comment", "\n",

      "x", ".", "1", ".", "a", "=", "12.2L", "\n   ",
      "y", ".", "2", ".", "b", "=", "13.4", " ", "# comment", "\n",
      "VALUE", " ", "value", " ", "VaLuE", " ", "Value1", " ", "17.00h", " ", "11", ".", "0", "k"
    };

    checkTypesAndTokens(sampleFile, types, matches);
  }

  public void testNumber() {
    String sample = "1.23=1.24";
    checkSameText(sample);
    IElementType[] types = {
      CustomHighlighterTokenType.NUMBER, CustomHighlighterTokenType.CHARACTER, CustomHighlighterTokenType.NUMBER
    };
    String[] matches = { "1.23", "=", "1.24" };
    checkTypesAndTokens(sample, types, matches);
  }

  public void testPostfix() {
    String sample = "abc 1.2ltext";
    checkSameText(sample);
    IElementType[] types = {
      CustomHighlighterTokenType.IDENTIFIER, CustomHighlighterTokenType.WHITESPACE,
      CustomHighlighterTokenType.NUMBER, CustomHighlighterTokenType.IDENTIFIER
    };
    String[] matches = { "abc", " ", "1.2l", "text" };
    checkTypesAndTokens(sample, types, matches);
  }

  public void testWeird() {
    String sample = "test.1.";
    checkSameText(sample);
    IElementType[] types = {
      CustomHighlighterTokenType.IDENTIFIER, CustomHighlighterTokenType.PUNCTUATION, CustomHighlighterTokenType.NUMBER
    };
    String[] matches = { "test", ".", "1." };
    checkTypesAndTokens(sample, types, matches);
  }

  public void testParenths() throws Exception {
    String sample = "value(255)";
    checkSameText(sample);
    IElementType[] types = {
      CustomHighlighterTokenType.KEYWORD_1, CustomHighlighterTokenType.CHARACTER,
      CustomHighlighterTokenType.NUMBER, CustomHighlighterTokenType.CHARACTER
    };
    String[] matches = { "value", "(", "255", ")" };
    checkTypesAndTokens(sample, types, matches);
  }
}
