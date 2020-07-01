package ru.adelf.idea.dotenv.grammars;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import ru.adelf.idea.dotenv.psi.DotEnvTypes;
import com.intellij.psi.TokenType;

import static ru.adelf.idea.dotenv.psi.DotEnvTypes.*;

%%

%class _DotEnvLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

CRLF=\R
WHITE_SPACE=[\ \t\f]
FIRST_VALUE_CHARACTER=[^ \n\f\r\"\\\#] | "\\".
VALUE_CHARACTER=[^\r\n\#]
QUOTED_VALUE_CHARACTER=[^\\\"] | \\.
ANY_CHARACTER=[^\r\n]
END_OF_LINE_COMMENT=("#")[^\r\n]*
SEPARATOR=[:=]
KEY_CHARACTER=[^:=\ \n\t\f\\] | "\\ "
QUOTE=[\"]
COMMENT=["#"]
EXPORT_PREFIX=[eE][xX][pP][oO][rR][tT](" ")+

%state WAITING_KEY
%state WAITING_VALUE
%state WAITING_QUOTED_VALUE
%state WAITING_COMMENT

%%

<YYINITIAL> {END_OF_LINE_COMMENT}                           { yybegin(YYINITIAL); return DotEnvTypes.COMMENT; }

<YYINITIAL> {KEY_CHARACTER}+                                { yybegin(YYINITIAL); return DotEnvTypes.KEY_CHARS; }

<YYINITIAL> {SEPARATOR}                                     { yybegin(WAITING_VALUE); return DotEnvTypes.SEPARATOR; }

<YYINITIAL> {EXPORT_PREFIX}                                 { yybegin(WAITING_KEY); return DotEnvTypes.EXPORT; }

<WAITING_KEY> {KEY_CHARACTER}+                              { yybegin(YYINITIAL); return DotEnvTypes.KEY_CHARS; }

<WAITING_VALUE> {CRLF}({CRLF}|{WHITE_SPACE})+               { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

<WAITING_VALUE> {WHITE_SPACE}+                              { yybegin(WAITING_VALUE); return TokenType.WHITE_SPACE; }

<WAITING_VALUE> {QUOTE}                                     { yybegin(WAITING_QUOTED_VALUE); return DotEnvTypes.QUOTE; }

<WAITING_VALUE> {COMMENT}                                   { yybegin(WAITING_COMMENT); return DotEnvTypes.COMMENT; }

<WAITING_VALUE> {FIRST_VALUE_CHARACTER}{VALUE_CHARACTER}*   { yybegin(YYINITIAL); return DotEnvTypes.VALUE_CHARS; }

<WAITING_QUOTED_VALUE> ({QUOTED_VALUE_CHARACTER})+          { yybegin(WAITING_QUOTED_VALUE); return DotEnvTypes.VALUE_CHARS; }

<WAITING_QUOTED_VALUE> {QUOTE}                              { yybegin(WAITING_COMMENT); return DotEnvTypes.QUOTE; }

<WAITING_COMMENT> {CRLF}({CRLF}|{WHITE_SPACE})+             { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

<WAITING_COMMENT> {ANY_CHARACTER}+                          { yybegin(WAITING_COMMENT); return DotEnvTypes.COMMENT; }

({CRLF}|{WHITE_SPACE})+                                     { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

[^]                                                         { return TokenType.BAD_CHARACTER; }