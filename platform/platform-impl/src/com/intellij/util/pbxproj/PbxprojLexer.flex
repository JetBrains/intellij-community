/* It's an automatically generated code. Do not modify it. */
package com.intellij.util.pbxproj;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

%%

%{
  public _PbxprojLexer() {
    this((java.io.Reader)null);
  }
%}


%unicode
%class _PbxprojLexer
%implements FlexLexer
%function advance
%type IElementType


WHITE_SPACE_CHAR=[\ \n\r\t\f]

VALUE=([:jletterdigit:]|[-/\.:])*

C_STYLE_COMMENT=("/*" {COMMENT_TAIL})|"/*"
COMMENT_TAIL=([^"*"]*("*"+[^"*""/"])?)*("*"+"/")?
END_OF_LINE_COMMENT="/""/"[^\r\n]*

STRING_LITERAL_DOUBLE_QUOTED=\"([^\\\"]|{ESCAPE_SEQUENCE})*(\"|\\)?
STRING_LITERAL_SINGLE_QUOTED=\'([^\\\']|{ESCAPE_SEQUENCE})*(\'|\\)?
HEX_LITERAL=<[^>]+(>)?
ESCAPE_SEQUENCE=\\[^\r\n]

%%

<YYINITIAL> {WHITE_SPACE_CHAR}+ { return PbxTokenType.WHITE_SPACE; }

<YYINITIAL> {C_STYLE_COMMENT} { return PbxTokenType.COMMENT; }
<YYINITIAL> {END_OF_LINE_COMMENT} { return PbxTokenType.COMMENT; }

<YYINITIAL> {STRING_LITERAL_DOUBLE_QUOTED} { return PbxTokenType.STRING_LITERAL; }
<YYINITIAL> {STRING_LITERAL_SINGLE_QUOTED} { return PbxTokenType.STRING_LITERAL; }
<YYINITIAL> {HEX_LITERAL}    { return PbxTokenType.STRING_LITERAL; }
<YYINITIAL> {VALUE}          { return PbxTokenType.VALUE; }
<YYINITIAL> "{"              { return PbxTokenType.LBRACE; }
<YYINITIAL> "}"              { return PbxTokenType.RBRACE; }
<YYINITIAL> "("              { return PbxTokenType.LPAR; }
<YYINITIAL> ")"              { return PbxTokenType.RPAR; }
<YYINITIAL> "="              { return PbxTokenType.EQ; }
<YYINITIAL> ","              { return PbxTokenType.COMMA; }
<YYINITIAL> ";"              { return PbxTokenType.SEMICOLON; }

[^]                          { return PbxTokenType.BAD_CHARACTER; }
