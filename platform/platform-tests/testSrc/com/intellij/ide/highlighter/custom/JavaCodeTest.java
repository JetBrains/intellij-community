package com.intellij.ide.highlighter.custom;

import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;

/**
 * @author Yura Cangea
 * @version 1.0
 */
public class JavaCodeTest extends CustomFileTypeLexerTestBase {

  @Override
  public SyntaxTable createSyntaxTable() {
    SyntaxTable table = new SyntaxTable();

    table.setLineComment("//");
    table.setStartComment("/*");
    table.setEndComment("*/");

    table.setHexPrefix("0x");
    table.setNumPostfixChars("cfdle");

    table.addKeyword1("package");
    table.addKeyword1("import");
    table.addKeyword1("this");
    table.addKeyword1("super");
    table.addKeyword1("public");
    table.addKeyword1("private");
    table.addKeyword1("protected");
    table.addKeyword1("null");
    table.addKeyword1("if");
    table.addKeyword1("else");
    table.addKeyword1("throws");
    table.addKeyword1("switch");
    table.addKeyword1("case");
    table.addKeyword1("break");
    table.addKeyword1("default");
    table.addKeyword1("continue");
    table.addKeyword1("goto");
    table.addKeyword1("boolean");
    table.addKeyword1("true");
    table.addKeyword1("false");
    table.addKeyword1("final");
    table.addKeyword1("class");
    table.addKeyword1("static");
    table.addKeyword1("final");
    table.addKeyword1("void");
    table.addKeyword1("int");
    table.addKeyword1("while");
    table.addKeyword1("new");
    table.addKeyword1("for");
    table.addKeyword1("byte");
    table.addKeyword1("float");
    table.addKeyword1("double");
    table.addKeyword1("short");
    table.addKeyword1("extends");
    table.addKeyword1("implements");
    table.addKeyword1("interface");
    table.addKeyword1("abstract");
    table.addKeyword1("char");
    table.addKeyword1("try");
    table.addKeyword1("catch");
    table.addKeyword1("finally");
    table.addKeyword1("synchronized");

    return table;
  }

  public void testParseSampleCode() {
    String sampleCode =
            "private some text f b g\n\n\n//   1\n  public static void main(String[] args) {\n}\n-10 - 10\n\"dsfdfdf\"\n/* a\n *bc */";
    String sampleCode2 = "/*";
    String sampleCode3 = "//";
    String sampleCode4 = "";
    String sampleCode5 = " ";

    checkSameText(sampleCode);
    String result;


    IElementType[] types = new IElementType[]{CustomHighlighterTokenType.KEYWORD_1, CustomHighlighterTokenType.WHITESPACE,
                                              CustomHighlighterTokenType.IDENTIFIER, CustomHighlighterTokenType.WHITESPACE, CustomHighlighterTokenType.IDENTIFIER, CustomHighlighterTokenType.WHITESPACE,
                                              CustomHighlighterTokenType.IDENTIFIER, CustomHighlighterTokenType.WHITESPACE, CustomHighlighterTokenType.IDENTIFIER, CustomHighlighterTokenType.WHITESPACE,
                                              CustomHighlighterTokenType.IDENTIFIER, CustomHighlighterTokenType.WHITESPACE,
                                              CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.WHITESPACE, CustomHighlighterTokenType.KEYWORD_1, CustomHighlighterTokenType.WHITESPACE,
                                              CustomHighlighterTokenType.KEYWORD_1, CustomHighlighterTokenType.WHITESPACE, CustomHighlighterTokenType.KEYWORD_1, CustomHighlighterTokenType.WHITESPACE,
                                              CustomHighlighterTokenType.IDENTIFIER, CustomHighlighterTokenType.CHARACTER, CustomHighlighterTokenType.IDENTIFIER, CustomHighlighterTokenType.CHARACTER,
                                              CustomHighlighterTokenType.CHARACTER, CustomHighlighterTokenType.WHITESPACE, CustomHighlighterTokenType.IDENTIFIER, CustomHighlighterTokenType.CHARACTER,
                                              CustomHighlighterTokenType.WHITESPACE, CustomHighlighterTokenType.CHARACTER, CustomHighlighterTokenType.WHITESPACE, CustomHighlighterTokenType.CHARACTER,
                                              CustomHighlighterTokenType.WHITESPACE, CustomHighlighterTokenType.CHARACTER, CustomHighlighterTokenType.NUMBER, CustomHighlighterTokenType.WHITESPACE,
                                              CustomHighlighterTokenType.CHARACTER, CustomHighlighterTokenType.WHITESPACE, CustomHighlighterTokenType.NUMBER, CustomHighlighterTokenType.WHITESPACE,
                                              CustomHighlighterTokenType.STRING, CustomHighlighterTokenType.WHITESPACE, CustomHighlighterTokenType.MULTI_LINE_COMMENT,
    };

    String[] matches = new String[]{"private", " ", "some", " ", "text", " ", "f", " ", "b", " ", "g", "\n\n\n", "//   1", "\n  ", "public",
                                    " ", "static", " ", "void", " ", "main", "(",
                                    "String", "[", "]", " ", "args", ")", " ", "{", "\n", "}", "\n", "-", "10",
                                    " ", "-", " ", "10", "\n", "\"dsfdfdf\"", "\n", "/* a\n *bc */",
    };

    checkTypesAndTokens(sampleCode, types, matches);

    lexer.start(sampleCode2);
    assertEquals(CustomHighlighterTokenType.MULTI_LINE_COMMENT, lexer.getTokenType());
    assertEquals("/*", sampleCode2.substring(lexer.getTokenStart(), lexer.getTokenEnd()));

    lexer.start(sampleCode3);
    assertEquals(CustomHighlighterTokenType.LINE_COMMENT, lexer.getTokenType());
    assertEquals("//", sampleCode3.substring(lexer.getTokenStart(), lexer.getTokenEnd()));

    lexer.start(sampleCode4);
    assertEquals(null, lexer.getTokenType());
    assertEquals("", sampleCode4.substring(lexer.getTokenStart(), lexer.getTokenEnd()));

    lexer.start(sampleCode5);
    assertEquals(CustomHighlighterTokenType.WHITESPACE, lexer.getTokenType());
    assertEquals(" ", sampleCode5.substring(lexer.getTokenStart(), lexer.getTokenEnd()));
  }

  public void testParseSampleCodeFromTo() {
    String sampleCode = "  int n=123;\n  float z=1;";
    lexer.start(sampleCode, 5, 5);
    assertEquals(lexer.getTokenType(), null);
    lexer.start(sampleCode, 5, 6);
    lexer.getTokenType();
    assertEquals(5, lexer.getTokenStart());
    assertEquals(6, lexer.getTokenEnd());
    assertEquals(6, lexer.getBufferEnd());
  }
}
