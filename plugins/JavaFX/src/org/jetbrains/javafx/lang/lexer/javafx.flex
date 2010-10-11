package org.jetbrains.javafx.lang.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import java.util.*;
import java.lang.reflect.Field;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;

%%

%unicode
%class _JavaFxLexer
%implements FlexLexer



%function advance
%type IElementType
%eof{ return;
%eof}

%{
  private BraceQuoteTracker myQuoteStack = BraceQuoteTracker.NULL_BQT;
%}

/* main character classes */
LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]

WhiteSpace = {LineTerminator} | [ \t\f]

NonZeroDigit = [1-9]
Digit = 0 | {NonZeroDigit}
OctDigit = [0-7]
HexDigit = [0-9A-Fa-f]

/* comments */
//Comment = {TraditionalComment} | {EndOfLineComment} | {DocumentationComment}

TraditionalComment = ("/*"[^"*"]{CommentTail})|"/*"
DocumentationComment = "/**"+("/"|([^"/""*"]{CommentTail}))?
CommentTail = ([^"*"]*("*"+[^"*""/"])?)*("*/")?
EndOfLineComment = "//" {InputCharacter}* {LineTerminator}?

/* identifiers */
Identifier = {JavaIdentifier}|{QuotedIdentifier}
JavaIdentifier = [:jletter:][:jletterdigit:]*
QuotedIdentifier = "<<" ([^>]|>[^>])*>* ">>"

/* integer literals */
DecIntegerLiteral = 0 | {NonZeroDigit} {Digit}*

HexIntegerLiteral = 0 [xX] {HexDigit}+

OctIntegerLiteral = 0+ {OctDigit}+

/* number literals */
NumberLiteral = ({FLit1}|{FLit2}|{FLit3}) {Exponent}?

FLit1    = {Digit}+ \. {Digit}+
FLit2    = \. {Digit}+
FLit3    = {Digit}+
Exponent = [eE] [+-]? {Digit}+

DurationLiteral = ({DecIntegerLiteral}|{NumberLiteral}) {TimeUnit}
TimeUnit = ms | s | m | h

/* string and character literals */
StringLiteral = \"{DoubleQuotedBody}(\"|\\)?
LbraceStringLiteral=\"{DoubleQuotedBody}\{
LbraceRbraceStringLiteral=\}{DoubleQuotedBody}\{
RbraceStringLiteral=\}{DoubleQuotedBody}(\"|\\)?

CharLiteral = \'{SingleQuotedBody}(\'|\\)?
LbraceCharLiteral=\'{SingleQuotedBody}\{
LbraceRbraceCharLiteral=\}{SingleQuotedBody}\{
RbraceCharLiteral=\}{SingleQuotedBody}(\'|\\)?

DoubleQuotedBody=({StringCharacter}|{EscapeSequence})*
SingleQuotedBody=({SingleCharacter}|{EscapeSequence})*

StringCharacter = [^\r\n\"\\{}]
SingleCharacter = [^\r\n\'\\{}]
EscapeSequence = \\[^\r\n]

LocalizationPrefix=##(\[[^\]]+\])?

%state STRING, CHAR

%%

<YYINITIAL> {

  /* keywords */
  "abstract"                     { return JavaFxTokenTypes.ABSTRACT_KEYWORD; }
  "after"                     { return JavaFxTokenTypes.AFTER_KEYWORD; }
  "and"                     { return JavaFxTokenTypes.AND_KEYWORD; }
  "as"                     { return JavaFxTokenTypes.AS_KEYWORD; }
  "assert"                     { return JavaFxTokenTypes.ASSERT_KEYWORD; }
  "at"                     { return JavaFxTokenTypes.AT_KEYWORD; }
  "attribute"                     { return JavaFxTokenTypes.ATTRIBUTE_KEYWORD; }
  "before"                     { return JavaFxTokenTypes.BEFORE_KEYWORD; }
  "bind"                     { return JavaFxTokenTypes.BIND_KEYWORD; }
  "bound"                     { return JavaFxTokenTypes.BOUND_KEYWORD; }
  "break"                     { return JavaFxTokenTypes.BREAK_KEYWORD; }
  "catch"                     { return JavaFxTokenTypes.CATCH_KEYWORD; }
  "class"                     { return JavaFxTokenTypes.CLASS_KEYWORD; }
  "continue"                     { return JavaFxTokenTypes.CONTINUE_KEYWORD; }
  "def"                     { return JavaFxTokenTypes.DEF_KEYWORD; }
  "delete"                     { return JavaFxTokenTypes.DELETE_KEYWORD; }
  "else"                     { return JavaFxTokenTypes.ELSE_KEYWORD; }
  "exclusive"                     { return JavaFxTokenTypes.EXCLUSIVE_KEYWORD; }
  "extends"                     { return JavaFxTokenTypes.EXTENDS_KEYWORD; }
  "false"                     { return JavaFxTokenTypes.FALSE_KEYWORD; }
  "finally"                     { return JavaFxTokenTypes.FINALLY_KEYWORD; }
  "first"                     { return JavaFxTokenTypes.FIRST_KEYWORD; }
  "for"                     { return JavaFxTokenTypes.FOR_KEYWORD; }
  "from"                     { return JavaFxTokenTypes.FROM_KEYWORD; }
  "function"                     { return JavaFxTokenTypes.FUNCTION_KEYWORD; }
  "if"                     { return JavaFxTokenTypes.IF_KEYWORD; }
  "import"                     { return JavaFxTokenTypes.IMPORT_KEYWORD; }
  "indexof"                     { return JavaFxTokenTypes.INDEXOF_KEYWORD; }
  "in"                     { return JavaFxTokenTypes.IN_KEYWORD; }
  "init"                     { return JavaFxTokenTypes.INIT_KEYWORD; }
  "insert"                     { return JavaFxTokenTypes.INSERT_KEYWORD; }
  "instanceof"                     { return JavaFxTokenTypes.INSTANCEOF_KEYWORD; }
  "into"                     { return JavaFxTokenTypes.INTO_KEYWORD; }
  "invalidate"                     { return JavaFxTokenTypes.INVALIDATE_KEYWORD; }
  "inverse"                     { return JavaFxTokenTypes.INVERSE_KEYWORD; }
  "last"                     { return JavaFxTokenTypes.LAST_KEYWORD; }
  "lazy"                     { return JavaFxTokenTypes.LAZY_KEYWORD; }
  "mixin"                     { return JavaFxTokenTypes.MIXIN_KEYWORD; }
  "mod"                     { return JavaFxTokenTypes.MOD_KEYWORD; }
  "new"                     { return JavaFxTokenTypes.NEW_KEYWORD; }
  "not"                     { return JavaFxTokenTypes.NOT_KEYWORD; }
  "null"                     { return JavaFxTokenTypes.NULL_KEYWORD; }
  "on"                     { return JavaFxTokenTypes.ON_KEYWORD; }
  "or"                     { return JavaFxTokenTypes.OR_KEYWORD; }
  "override"                     { return JavaFxTokenTypes.OVERRIDE_KEYWORD; }
  "package"                     { return JavaFxTokenTypes.PACKAGE_KEYWORD; }
  "postinit"                     { return JavaFxTokenTypes.POSTINIT_KEYWORD; }
  "private"                     { return JavaFxTokenTypes.PRIVATE_KEYWORD; }
  "protected"                     { return JavaFxTokenTypes.PROTECTED_KEYWORD; }
  "public-init"                     { return JavaFxTokenTypes.PUBLIC_INIT_KEYWORD; }
  "public"                     { return JavaFxTokenTypes.PUBLIC_KEYWORD; }
  "public-read"                     { return JavaFxTokenTypes.PUBLIC_READ_KEYWORD; }
  "replace"                     { return JavaFxTokenTypes.REPLACE_KEYWORD; }
  "return"                     { return JavaFxTokenTypes.RETURN_KEYWORD; }
  "reverse"                     { return JavaFxTokenTypes.REVERSE_KEYWORD; }
  "sizeof"                     { return JavaFxTokenTypes.SIZEOF_KEYWORD; }
  "static"                     { return JavaFxTokenTypes.STATIC_KEYWORD; }
  "step"                     { return JavaFxTokenTypes.STEP_KEYWORD; }
  "super"                     { return JavaFxTokenTypes.SUPER_KEYWORD; }
  "then"                     { return JavaFxTokenTypes.THEN_KEYWORD; }
  "this"                     { return JavaFxTokenTypes.THIS_KEYWORD; }
  "throw"                     { return JavaFxTokenTypes.THROW_KEYWORD; }
  "trigger"                     { return JavaFxTokenTypes.TRIGGER_KEYWORD; }
  "true"                     { return JavaFxTokenTypes.TRUE_KEYWORD; }
  "try"                     { return JavaFxTokenTypes.TRY_KEYWORD; }
  "tween"                     { return JavaFxTokenTypes.TWEEN_KEYWORD; }
  "typeof"                     { return JavaFxTokenTypes.TYPEOF_KEYWORD; }
  "var"                     { return JavaFxTokenTypes.VAR_KEYWORD; }
  "where"                     { return JavaFxTokenTypes.WHERE_KEYWORD; }
  "while"                     { return JavaFxTokenTypes.WHILE_KEYWORD; }
  "with"                     { return JavaFxTokenTypes.WITH_KEYWORD; }

  /* separators */
  "("                            { return (JavaFxTokenTypes.LPAREN); }
  ")"                            { return (JavaFxTokenTypes.RPAREN); }
  "["                            { return (JavaFxTokenTypes.LBRACK); }
  "]"                            { return (JavaFxTokenTypes.RBRACK); }
  ";"                            { return (JavaFxTokenTypes.SEMICOLON); }
  ","                            { return (JavaFxTokenTypes.COMMA); }
  "."                            { return (JavaFxTokenTypes.DOT); }
  ".."                           { return (JavaFxTokenTypes.RANGE); }
  ":"                            { return (JavaFxTokenTypes.COLON); }
  "|"                            { return (JavaFxTokenTypes.DELIM); }

  "{"  {
    myQuoteStack.enterBrace();
    return (JavaFxTokenTypes.LBRACE);
  }
  "}"  {
    final int state = myQuoteStack.leaveBrace();
    if (state == -1) {
      return (JavaFxTokenTypes.RBRACE);
    }
    zzMarkedPos = zzCurrentPos;
    yybegin(state);
  }

  /* operators */
  ">"                            { return (JavaFxTokenTypes.GT); }
  "<"                            { return (JavaFxTokenTypes.LT); }
  "=="                           { return (JavaFxTokenTypes.EQEQ); }
  "<="                           { return (JavaFxTokenTypes.LTEQ); }
  ">="                           { return (JavaFxTokenTypes.GTEQ); }
  "=>"                           { return (JavaFxTokenTypes.EQGT); }
  "!="                           { return (JavaFxTokenTypes.NOTEQ); }

  "+="                           { return (JavaFxTokenTypes.PLUSEQ); }
  "-="                           { return (JavaFxTokenTypes.MINUSEQ); }
  "*="                           { return (JavaFxTokenTypes.MULTEQ); }
  "/="                           { return (JavaFxTokenTypes.DIVEQ); }

  "++"                           { return (JavaFxTokenTypes.PLUSPLUS); }
  "--"                           { return (JavaFxTokenTypes.MINUSMINUS); }
  "+"                            { return (JavaFxTokenTypes.PLUS); }
  "-"                            { return (JavaFxTokenTypes.MINUS); }
  "*"                            { return (JavaFxTokenTypes.MULT); }
  "/"                            { return (JavaFxTokenTypes.DIV); }
  "="                            { return (JavaFxTokenTypes.EQ); }
  ///* string literal */
  //\"                             { yybegin(STRING); string.setLength(0); }
  //
  ///* character literal */
  //\'                             { yybegin(CHARLITERAL); string.setLength(0);}

  /* numeric literals */

  {DecIntegerLiteral}            { return JavaFxTokenTypes.INTEGER_LITERAL; }
  {HexIntegerLiteral}            { return JavaFxTokenTypes.INTEGER_LITERAL; }
  {OctIntegerLiteral}            { return JavaFxTokenTypes.INTEGER_LITERAL; }
  {NumberLiteral}                { return JavaFxTokenTypes.NUMBER_LITERAL; }
  {DurationLiteral}              { return JavaFxTokenTypes.DURATION_LITERAL; }
  
  /* comments */
  {TraditionalComment}           { return JavaFxTokenTypes.C_STYLE_COMMENT; }
  {EndOfLineComment}             { return JavaFxTokenTypes.END_OF_LINE_COMMENT; }
  {DocumentationComment}         { return JavaFxTokenTypes.DOC_COMMENT; }

  /* whitespace */
  {WhiteSpace}                   { return JavaFxTokenTypes.WHITE_SPACE; }

  /* identifiers */
  {Identifier}                   { return JavaFxTokenTypes.IDENTIFIER; }

  /* string literals */
  \"              { zzMarkedPos = zzCurrentPos; yybegin(STRING); }

  \'              { zzMarkedPos = zzCurrentPos; yybegin(CHAR); }

  {LocalizationPrefix}           { return JavaFxTokenTypes.LOCALIZATION_PREFIX; }
}

<STRING> {
  {StringLiteral}                            { yybegin(YYINITIAL); return JavaFxTokenTypes.STRING_LITERAL; }

  {LbraceStringLiteral} {
    yybegin(YYINITIAL);
    myQuoteStack = myQuoteStack.enterBrace(STRING, false);
    return JavaFxTokenTypes.LBRACE_STRING_LITERAL;
  }

  {LbraceRbraceStringLiteral} {
    yybegin(YYINITIAL);
    myQuoteStack.enterBrace();
    return JavaFxTokenTypes.LBRACE_RBRACE_STRING_LITERAL;
  }

  {RbraceStringLiteral} {
    yybegin(YYINITIAL);
    myQuoteStack = myQuoteStack.leaveQuote();
    return JavaFxTokenTypes.RBRACE_STRING_LITERAL;
  }
}

<CHAR> {
  {CharLiteral}                            { yybegin(YYINITIAL); return JavaFxTokenTypes.STRING_LITERAL; }

  {LbraceCharLiteral} {
    yybegin(YYINITIAL);
    myQuoteStack = myQuoteStack.enterBrace(CHAR, false);
    return JavaFxTokenTypes.LBRACE_STRING_LITERAL;
  }

  {LbraceRbraceCharLiteral} {
    yybegin(YYINITIAL);
    myQuoteStack.enterBrace();
    return JavaFxTokenTypes.LBRACE_RBRACE_STRING_LITERAL;
  }
  
  {RbraceCharLiteral} {
    yybegin(YYINITIAL);
    myQuoteStack = myQuoteStack.leaveQuote();
    return JavaFxTokenTypes.RBRACE_STRING_LITERAL;
  }
}

/* error fallback */
.                                { return JavaFxTokenTypes.BAD_CHARACTER; }
