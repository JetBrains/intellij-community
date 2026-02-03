package ru.adelf.idea.dotenv.grammars;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import ru.adelf.idea.dotenv.psi.DotEnvTypes;
import com.intellij.psi.TokenType;

%%

%class _DotEnvLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType

CRLF=\R
WHITE_SPACE=[\ \t\f]
FIRST_VALUE_CHARACTER=[^ \n\f\r\"\'\\\#] | "\\".
VALUE_CHARACTER=[^\r\n\#]
ANY_CHARACTER=[^\r\n]
QUOTE_VALUE_CHARACTER=[^\\\"\$]
DOLLAR_CHARACTER=[\$]
NON_SYNTACTIC_DOLLAR_CHARACTER={DOLLAR_CHARACTER}[^\{\"]
QUOTED_SYMBOL=[\\][^]
SINGLE_QUOTE_VALUE_CHARACTER=[^\\\']
END_OF_LINE_COMMENT=("#")[^\r\n]*
SEPARATOR=[:=]
KEY_CHARACTER=[^:=\ \n\t\f\\\${}] | "\\ "
QUOTE=[\"]
SINGLE_QUOTE=[\']
COMMENT=["#"]
EXPORT_PREFIX=[eE][xX][pP][oO][rR][tT](" ")+
NESTED_VARIABLE_START="${"
NESTED_VARIABLE_END={WHITE_SPACE}* "}"

%state WAITING_KEY
%state WAITING_VALUE
%state WAITING_QUOTED_VALUE
%state WAITING_SINGLE_QUOTED_VALUE
%state WAITING_COMMENT
%state WAITING_NESTED_VARIABLE
%state WAITING_NESTED_VARIABLE_END

%%

<YYINITIAL> {END_OF_LINE_COMMENT}                           { yybegin(YYINITIAL); return DotEnvTypes.COMMENT; }

<YYINITIAL> {KEY_CHARACTER}+                                { yybegin(YYINITIAL); return DotEnvTypes.KEY_CHARS; }

<YYINITIAL> {SEPARATOR}                                     { yybegin(WAITING_VALUE); return DotEnvTypes.SEPARATOR; }

<YYINITIAL> {EXPORT_PREFIX}                                 { yybegin(WAITING_KEY); return DotEnvTypes.EXPORT; }

<WAITING_KEY> {KEY_CHARACTER}+                              { yybegin(YYINITIAL); return DotEnvTypes.KEY_CHARS; }

<WAITING_VALUE> {CRLF}({CRLF}|{WHITE_SPACE})+               { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

<WAITING_VALUE> {WHITE_SPACE}+                              { yybegin(WAITING_VALUE); return TokenType.WHITE_SPACE; }

<WAITING_VALUE> {QUOTE}                                     { yybegin(WAITING_QUOTED_VALUE); return DotEnvTypes.QUOTE; }

<WAITING_VALUE> {SINGLE_QUOTE}                              { yybegin(WAITING_SINGLE_QUOTED_VALUE); return DotEnvTypes.QUOTE; }

<WAITING_VALUE> {COMMENT}                                   { yybegin(WAITING_COMMENT); return DotEnvTypes.COMMENT; }

<WAITING_VALUE> {FIRST_VALUE_CHARACTER}{VALUE_CHARACTER}*   { yybegin(YYINITIAL); return DotEnvTypes.VALUE_CHARS; }

<WAITING_QUOTED_VALUE> {QUOTE}                              { yybegin(WAITING_COMMENT); return DotEnvTypes.QUOTE; }

<WAITING_QUOTED_VALUE> {NESTED_VARIABLE_START}              { yybegin(WAITING_NESTED_VARIABLE); return DotEnvTypes.NESTED_VARIABLE_START; }

<WAITING_NESTED_VARIABLE> {KEY_CHARACTER}+                  { yybegin(WAITING_NESTED_VARIABLE_END); return DotEnvTypes.KEY_CHARS; }

<WAITING_NESTED_VARIABLE_END, WAITING_NESTED_VARIABLE> {NESTED_VARIABLE_END}         { yybegin(WAITING_QUOTED_VALUE); return DotEnvTypes.NESTED_VARIABLE_END; }

<WAITING_QUOTED_VALUE> ({QUOTE_VALUE_CHARACTER}|{QUOTED_SYMBOL}|{NON_SYNTACTIC_DOLLAR_CHARACTER})+ { yybegin(WAITING_QUOTED_VALUE); return DotEnvTypes.VALUE_CHARS; }

<WAITING_QUOTED_VALUE> {DOLLAR_CHARACTER} / {QUOTE} { yybegin(WAITING_QUOTED_VALUE); return DotEnvTypes.VALUE_CHARS; }

<WAITING_SINGLE_QUOTED_VALUE> {SINGLE_QUOTE}                { yybegin(WAITING_COMMENT); return DotEnvTypes.QUOTE; }

<WAITING_SINGLE_QUOTED_VALUE> ({SINGLE_QUOTE_VALUE_CHARACTER}|{QUOTED_SYMBOL})+ { yybegin(WAITING_SINGLE_QUOTED_VALUE); return DotEnvTypes.VALUE_CHARS; }

<WAITING_COMMENT> {CRLF}({CRLF}|{WHITE_SPACE})+             { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

<WAITING_COMMENT> {ANY_CHARACTER}+                          { yybegin(WAITING_COMMENT); return DotEnvTypes.COMMENT; }

({CRLF}|{WHITE_SPACE})+                                     { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

[^]                                                         { return TokenType.BAD_CHARACTER; }