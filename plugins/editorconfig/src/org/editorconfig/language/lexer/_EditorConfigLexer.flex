package org.editorconfig.language.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import kotlin.NotImplementedError;
import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static org.editorconfig.language.psi.EditorConfigElementTypes.*;

%%

%{
  private int myPreviousState = YYINITIAL;

  public _EditorConfigLexer() {
    this(null);
  }
%}

%public
%class _EditorConfigLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

WHITE_SPACE=\s+

LINE_COMMENT=[#;].*

PATTERN_WHITE_SPACE=\s+
PATTERN_LETTER=[^:=\s\[\]\{\}\\,*?!#;]|\\.|\\\R
IDENTIFIER_EDGE_LETTER=[^:=\s\[\],#;\.]
IDENTIFIER_LETTER=[^:=\r\n\[\],#;\.]

PATTERN_IDENTIFIER=({PATTERN_LETTER})+
IDENTIFIER={IDENTIFIER_EDGE_LETTER}|({IDENTIFIER_EDGE_LETTER}{IDENTIFIER_LETTER}*{IDENTIFIER_EDGE_LETTER})

%state YYHEADER
%state YYCHARCLASS

%%

"="                         { yybegin(YYINITIAL); return SEPARATOR; }
","                         { return COMMA; }
{LINE_COMMENT}              { return LINE_COMMENT; }

<YYCHARCLASS> {
  {PATTERN_LETTER}          { return CHARCLASS_LETTER; }
  "!"                       { return EXCLAMATION; }
  "]"                       { yybegin(YYHEADER); return R_BRACKET; }
}

<YYHEADER> {
  "**"                      { return DOUBLE_ASTERISK; }
  "*"                       { return ASTERISK; }
  "?"                       { return QUESTION; }
  "["                       { yybegin(YYCHARCLASS); return L_BRACKET; }
  "]"                       { yybegin(YYINITIAL); return R_BRACKET; }
  "{"                       { return L_CURLY; }
  "}"                       { return R_CURLY; }
  {PATTERN_WHITE_SPACE}     { return PATTERN_WHITE_SPACE; }
  {PATTERN_IDENTIFIER}      { return PATTERN_IDENTIFIER; }
}

<YYINITIAL> {
  "."                       { return DOT; }
  "["                       { yybegin(YYHEADER); return L_BRACKET; }
  "]"                       { return R_BRACKET; }
  ":"                       { return COLON; }
  {WHITE_SPACE}             { return WHITE_SPACE; }
  {IDENTIFIER}              { return IDENTIFIER; }
}

[^]                         { return BAD_CHARACTER; }
