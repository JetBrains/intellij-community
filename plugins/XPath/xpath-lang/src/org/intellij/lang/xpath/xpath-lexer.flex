/* It's an automatically generated code. Do not modify it. */
package org.intellij.lang.xpath;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

@SuppressWarnings({"ALL"})
%%

%class _XPathLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

WHITE_SPACE_CHAR=[\ \n\r\t]

NCNameChar=[:letter:] | [:digit:] | "." | "-" | "_"
NCName=([:letter:] | "_") {NCNameChar}*
QName=({NCName} ":")? {NCName}

STRING_LITERAL= "\"" [^\"]* "\""? | "'" [^\']* "'"?
DIGITS=[:digit:]+
NUMBER= {DIGITS} ("." {DIGITS}?)? | "." {DIGITS}

AXIS= "ancestor"
    | "ancestor-or-self"
    | "attribute"
    | "child"
    | "descendant"
    | "descendant-or-self"
    | "following"
    | "following-sibling"
    | "namespace"
    | "parent"
    | "preceding"
    | "preceding-sibling"
    | "self"

NODE_TYPE= "comment"
    | "text"
    | "processing-instruction"
    | "node"

WS={WHITE_SPACE_CHAR}+

%state S1
%state FUNC
%state VAR

%%

{WS}                        { return XPathTokenTypes.WHITESPACE; }

"$"                         { yybegin(VAR);         return XPathTokenTypes.DOLLAR; }
<VAR>
    {NCName} /
    {WS}? ":"               {                      return XPathTokenTypes.VARIABLE_PREFIX; }
<VAR> ":"                   {                      return XPathTokenTypes.COL; }
<VAR> {NCName}              { yybegin(S1);         return XPathTokenTypes.VARIABLE_NAME; }

{STRING_LITERAL}            { yybegin(S1);          return XPathTokenTypes.STRING_LITERAL; }
{NUMBER}                    { yybegin(S1);          return XPathTokenTypes.NUMBER; }

{NODE_TYPE} / {WS}? "("     { yybegin(S1);          return XPathTokenTypes.NODE_TYPE; }

<YYINITIAL>
    {NCName} /
    {WS}? ":" {NCName}
    {WS}? "("               { yybegin(FUNC);        return XPathTokenTypes.EXT_PREFIX; }
<YYINITIAL>
    {NCName} / {WS}? "("    {                       return XPathTokenTypes.FUNCTION_NAME; }
<FUNC> ":"                  {                       return XPathTokenTypes.COL; }
<FUNC> {NCName}             {                       return XPathTokenTypes.FUNCTION_NAME; }

<S1> "*"                    { yybegin(YYINITIAL);   return XPathTokenTypes.MULT; }
<YYINITIAL> "*"             { yybegin(S1);          return XPathTokenTypes.STAR; }

<S1> "and"                  { yybegin(YYINITIAL);   return XPathTokenTypes.AND; }
<S1> "or"                   { yybegin(YYINITIAL);   return XPathTokenTypes.OR; }
<S1> "div"                  { yybegin(YYINITIAL);   return XPathTokenTypes.DIV; }
<S1> "mod"                  { yybegin(YYINITIAL);   return XPathTokenTypes.MOD; }

{AXIS}   / {WS}? "::"       { yybegin(S1);          return XPathTokenTypes.AXIS_NAME; }
{NCName} / {WS}? "::"       { yybegin(S1);          return XPathTokenTypes.BAD_AXIS_NAME; }

{NCName}                    { yybegin(S1);          return XPathTokenTypes.NCNAME; }

"@"                         { yybegin(YYINITIAL);   return XPathTokenTypes.AT; }
"::"                        { yybegin(YYINITIAL);   return XPathTokenTypes.COLCOL; }
","                         { yybegin(YYINITIAL);   return XPathTokenTypes.COMMA; }
"."                         { yybegin(S1);          return XPathTokenTypes.DOT; }
".."                        { yybegin(S1);          return XPathTokenTypes.DOTDOT; }
":"                         { yybegin(YYINITIAL);   return XPathTokenTypes.COL; }

"/"                         { yybegin(YYINITIAL);   return XPathTokenTypes.PATH; }
"//"                        { yybegin(YYINITIAL);   return XPathTokenTypes.ANY_PATH; }
"|"                         { yybegin(YYINITIAL);   return XPathTokenTypes.UNION; }

"+"                         { yybegin(YYINITIAL);   return XPathTokenTypes.PLUS; }
"-"                         { yybegin(YYINITIAL);   return XPathTokenTypes.MINUS; }
"="                         { yybegin(YYINITIAL);   return XPathTokenTypes.EQ; }
"!="                        { yybegin(YYINITIAL);   return XPathTokenTypes.NE; }
"<"  | "&lt;"               { yybegin(YYINITIAL);   return XPathTokenTypes.LT; }
">"  | "&gt;"               { yybegin(YYINITIAL);   return XPathTokenTypes.GT; }
"<=" | "&lt;="              { yybegin(YYINITIAL);   return XPathTokenTypes.LE; }
">=" | "&gt;="              { yybegin(YYINITIAL);   return XPathTokenTypes.GE; }

"("                         { yybegin(YYINITIAL);   return XPathTokenTypes.LPAREN; }
")"                         { yybegin(S1);          return XPathTokenTypes.RPAREN; }
"["                         { yybegin(YYINITIAL);   return XPathTokenTypes.LBRACKET; }
"]"                         { yybegin(S1);          return XPathTokenTypes.RBRACKET; }

"{"                         { return XPathTokenTypes.LBRACE; }
"}"                         { return XPathTokenTypes.RBRACE; }

.                           { yybegin(YYINITIAL);   return XPathTokenTypes.BAD_CHARACTER; }