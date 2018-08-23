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
FIRST_VALUE_CHARACTER=[^ \n\f\r\\] | "\\".
VALUE_CHARACTER=[^\r\n]
END_OF_LINE_COMMENT=("#"|"//")[^\r\n]*
SEPARATOR=[:=]
KEY_CHARACTER=[^:=\ \n\t\f\\] | "\\ "

%state WAITING_VALUE

%%

<YYINITIAL> {END_OF_LINE_COMMENT}                           { yybegin(YYINITIAL); return DotEnvTypes.COMMENT; }

<YYINITIAL> {KEY_CHARACTER}+                                { yybegin(YYINITIAL); return DotEnvTypes.KEY_CHARS; }

<YYINITIAL> {SEPARATOR}                                     { yybegin(WAITING_VALUE); return DotEnvTypes.SEPARATOR; }

<WAITING_VALUE> {CRLF}({CRLF}|{WHITE_SPACE})+               { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

<WAITING_VALUE> {WHITE_SPACE}+                              { yybegin(WAITING_VALUE); return TokenType.WHITE_SPACE; }

<WAITING_VALUE> {FIRST_VALUE_CHARACTER}{VALUE_CHARACTER}*   { yybegin(YYINITIAL); return DotEnvTypes.VALUE_CHARS; }

({CRLF}|{WHITE_SPACE})+                                     { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

[^]                                                         { return TokenType.BAD_CHARACTER; }