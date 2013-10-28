package com.jetbrains.json;
import com.intellij.lexer.*;
import com.intellij.psi.tree.IElementType;
import static com.jetbrains.json.JsonParserTypes.*;

%%

%{
  public _JsonLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _JsonLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode
%eof{ return;
%eof}

WHITE_SPACE=[ \t\n\r]

L_CURLY="{"
R_CURLY="}"
L_BRAKET="["
R_BRAKET="]"
COMMA=","
COLON=":"
STRING=\"([^\\\"]|\\([\\\"/bfnrt]|u[a-fA-F0-9]{4}))*\"
NUMBER=-?[:digit:]+(\.[:digit:]+([eE][+-]?[:digit:]+)?)?
TRUE="true"
FALSE="false"
NULL="null"


%%
<YYINITIAL> {
{L_CURLY}                { return L_CURLY; }
{R_CURLY}                { return R_CURLY; }
{L_BRAKET}                { return L_BRAKET; }
{R_BRAKET}                { return R_BRAKET; }
{COMMA}                { return COMMA; }
{COLON}                { return COLON; }
{STRING}                { return STRING; }
{NUMBER}                { return NUMBER; }
{TRUE}                { return TRUE; }
{FALSE}                { return FALSE; }
{NULL}                { return NULL; }

{WHITE_SPACE}+  { return com.intellij.psi.TokenType.WHITE_SPACE; }
[^]             { return com.intellij.psi.TokenType.BAD_CHARACTER; }
}
