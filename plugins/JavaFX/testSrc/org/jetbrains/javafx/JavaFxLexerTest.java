package org.jetbrains.javafx;

import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.javafx.lang.lexer.JavaFxFlexLexer;

import static org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes.*;

/**
 * testing all sides of JavaFx lexer
 *
 * @author andrey, Alexey.Ivanov
 */
public class JavaFxLexerTest extends UsefulTestCase {
  /**
   * Tests input stream, checking that it contains expected tokens,
   * whitespace are ignored
   */
  private static void doTest(CharSequence buffer, IElementType... expected) {
    JavaFxFlexLexer lexer = new JavaFxFlexLexer();
    lexer.start(buffer, 0, buffer.length(), 0);
    int index = 0;

    while (true) {
      final IElementType token = lexer.getTokenType();
      if (token == null) {
        fail("EOF reached, token " + expected[index] + " not found");
      }
      lexer.advance();
      if (!WHITESPACES.contains(token)) {
        assertSame("Index: " + index, token, expected[index]);
        ++index;
        if (index == expected.length) {
          return;
        }
      }
    }
  }

  public void testLiterals() {
    doTest("\"ss\" \'ss\' 0 012 0xff 0.0 1e+12 0.2e-3 4e5 .1 true false 0ms 10s 0.5h",
           STRING_LITERAL, STRING_LITERAL, INTEGER_LITERAL, INTEGER_LITERAL, INTEGER_LITERAL,
           NUMBER_LITERAL, NUMBER_LITERAL, NUMBER_LITERAL, NUMBER_LITERAL, NUMBER_LITERAL, TRUE_KEYWORD, FALSE_KEYWORD,
           DURATION_LITERAL, DURATION_LITERAL, DURATION_LITERAL);
  }

  public void testSequences() {
    doTest("for (x in [1..5]) { [x, x*x] }",
           FOR_KEYWORD, LPAREN, IDENTIFIER, IN_KEYWORD, LBRACK, INTEGER_LITERAL, RANGE, INTEGER_LITERAL, RBRACK, RPAREN,
           LBRACE, LBRACK, IDENTIFIER, COMMA, IDENTIFIER, MULT, IDENTIFIER, RBRACK, RBRACE);
  }

  public void testInsert() {
    doTest("insert 'Daz' after names[3]",
           INSERT_KEYWORD, STRING_LITERAL, AFTER_KEYWORD, IDENTIFIER, LBRACK, INTEGER_LITERAL, RBRACK);
  }

  public void testObjectLiteral() {
    doTest("class Point { var x : Number; var y : Integer; } def somewhere = Point { x: 3.2; y: 7 }",
           CLASS_KEYWORD, IDENTIFIER, LBRACE, VAR_KEYWORD, IDENTIFIER, COLON, IDENTIFIER, SEMICOLON,
           VAR_KEYWORD, IDENTIFIER, COLON, IDENTIFIER, SEMICOLON, RBRACE,
           DEF_KEYWORD, IDENTIFIER, EQ, IDENTIFIER, LBRACE, IDENTIFIER, COLON, NUMBER_LITERAL, SEMICOLON,
           IDENTIFIER, COLON, INTEGER_LITERAL, RBRACE);
  }

  public void testIdentifiers() {
    doTest("aaa some_text _id a3 _ ",
           IDENTIFIER, IDENTIFIER, IDENTIFIER, IDENTIFIER, IDENTIFIER);
  }

  public void testKeywords() {
    doTest("abstract after and as assert at attribute " +
           "before bind bound break catch class continue " +
           "def delete else exclusive extends " +
           "false finally first for from function " +
           "if import indexof in init insert instanceof into inverse " +
           "last lazy mixin mod new not null on or override " +
           "package postinit private protected public-init public public-read " +
           "replace return reverse sizeof static step super " +
           "then this throw trigger true try tween typeof var where while with invalidate",
           ABSTRACT_KEYWORD, AFTER_KEYWORD, AND_KEYWORD, AS_KEYWORD, ASSERT_KEYWORD, AT_KEYWORD, ATTRIBUTE_KEYWORD,
           BEFORE_KEYWORD, BIND_KEYWORD, BOUND_KEYWORD, BREAK_KEYWORD, CATCH_KEYWORD, CLASS_KEYWORD, CONTINUE_KEYWORD,
           DEF_KEYWORD, DELETE_KEYWORD, ELSE_KEYWORD, EXCLUSIVE_KEYWORD, EXTENDS_KEYWORD,
           FALSE_KEYWORD, FINALLY_KEYWORD, FIRST_KEYWORD, FOR_KEYWORD, FROM_KEYWORD, FUNCTION_KEYWORD,
           IF_KEYWORD, IMPORT_KEYWORD, INDEXOF_KEYWORD, IN_KEYWORD, INIT_KEYWORD,
           INSERT_KEYWORD, INSTANCEOF_KEYWORD, INTO_KEYWORD, INVERSE_KEYWORD,
           LAST_KEYWORD, LAZY_KEYWORD, MIXIN_KEYWORD, MOD_KEYWORD, NEW_KEYWORD,
           NOT_KEYWORD, NULL_KEYWORD, ON_KEYWORD, OR_KEYWORD, OVERRIDE_KEYWORD,
           PACKAGE_KEYWORD, POSTINIT_KEYWORD, PRIVATE_KEYWORD,
           PROTECTED_KEYWORD, PUBLIC_INIT_KEYWORD, PUBLIC_KEYWORD, PUBLIC_READ_KEYWORD,
           REPLACE_KEYWORD, RETURN_KEYWORD, REVERSE_KEYWORD,
           SIZEOF_KEYWORD, STATIC_KEYWORD, STEP_KEYWORD, SUPER_KEYWORD,
           THEN_KEYWORD, THIS_KEYWORD, THROW_KEYWORD, TRIGGER_KEYWORD, TRUE_KEYWORD, TRY_KEYWORD, TWEEN_KEYWORD, TYPEOF_KEYWORD,
           VAR_KEYWORD, WHERE_KEYWORD, WHILE_KEYWORD, WITH_KEYWORD, INVALIDATE_KEYWORD);
  }

  public void testEscapes() {
    doTest("\"\\.?[^\\.]+$\t\"", STRING_LITERAL);
    doTest("'\\.?[^\\.]+$\t'", STRING_LITERAL);
    doTest("\"{packageName}/{scriptFileName.replaceAll('\\\\.[fF][xX]$', '')}\"", LBRACE_STRING_LITERAL, IDENTIFIER,
           LBRACE_RBRACE_STRING_LITERAL, IDENTIFIER, DOT, IDENTIFIER, LPAREN, STRING_LITERAL, COMMA, STRING_LITERAL, RPAREN,
           RBRACE_STRING_LITERAL);
  }

  public void testLocalizationStrings() {
    doTest("##\"text\"; ##[key]\'value\'", LOCALIZATION_PREFIX, STRING_LITERAL, SEMICOLON, LOCALIZATION_PREFIX, STRING_LITERAL);
  }

  public void testComments() {
    doTest("// comment \n /*comment */ /** comment */", END_OF_LINE_COMMENT, C_STYLE_COMMENT, DOC_COMMENT);
    doTest("/* comment \n comment */ /**\n comment */", C_STYLE_COMMENT, DOC_COMMENT);
    doTest("/* comment ", C_STYLE_COMMENT);
  }

  public void testQuotedIdentifiers() {
    doTest("<<identifier>> <<2 + 3 * 5a>> << <a>b> >> <<a>>>>", IDENTIFIER, IDENTIFIER, IDENTIFIER, IDENTIFIER);
  }

  public void testFormat() {
    doTest("\"He{{\"l{\"l\"}o\" \" \"}}world\" } {}", LBRACE_STRING_LITERAL, LBRACE, LBRACE_STRING_LITERAL,
           STRING_LITERAL, RBRACE_STRING_LITERAL, STRING_LITERAL, RBRACE, RBRACE_STRING_LITERAL, RBRACE, LBRACE, RBRACE);
    doTest("\'He{{\"l{\'l\'}o\" \'{ \' \' }\'}}world\' } {}", LBRACE_STRING_LITERAL, LBRACE, LBRACE_STRING_LITERAL,
           STRING_LITERAL, RBRACE_STRING_LITERAL, LBRACE_STRING_LITERAL, STRING_LITERAL, RBRACE_STRING_LITERAL,
           RBRACE, RBRACE_STRING_LITERAL, RBRACE, LBRACE, RBRACE);
  }
}
