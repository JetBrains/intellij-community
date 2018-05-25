// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.Stack;
import static org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes.*;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;

%%

%class _GroovyLexer
%extends GroovyLexerBase
%implements FlexLexer
%unicode
%public
%function advance
%type IElementType

%{
  @Override
  protected int getInitialState() {
    return YYINITIAL;
  }

  @Override
  protected int getDivisionExpectedState() {
    return DIVISION_EXPECTED;
  }

  @Override
  protected int[] getDivisionStates() {
    return new int[] {YYINITIAL, IN_INNER_BLOCK};
  }
%}

%state IN_INNER_BLOCK

%xstate DIVISION_EXPECTED

%xstate IN_SINGLE_GSTRING
%xstate IN_TRIPLE_GSTRING
%xstate IN_SLASHY_STRING
%xstate IN_DOLLAR_SLASH_STRING

%xstate IN_GSTRING_DOLLAR
%xstate IN_GSTRING_DOT
%xstate IN_GSTRING_DOT_IDENT

// Not to separate NewLine sequence by comments
%xstate NLS_AFTER_COMMENT
// Special hacks for IDEA formatter
%xstate NLS_AFTER_LBRACE
%xstate NLS_AFTER_NLS

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// NewLines and spaces /////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mONE_NL = \R                                                        // NewLines
WHITE_SPACE = " " | \t | \f | \\ {mONE_NL}                          // Whitespaces
mNLS = {mONE_NL}({mONE_NL}|{WHITE_SPACE})*

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Comments ////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

C_STYLE_COMMENT=("/*" [^"*"] {COMMENT_TAIL} ) | "/*"
COMMENT_TAIL=( [^"*"]* ("*"+ [^"*""/"] )? )* ("*" | "*"+"/")?

mSH_COMMENT = "#!"[^\r\n]*
mSL_COMMENT = "/""/"[^\r\n]*
mML_COMMENT = {C_STYLE_COMMENT}
mDOC_COMMENT="/*" "*"+ ( "/" | ( [^"/""*"] {COMMENT_TAIL} ) )?

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Numbers /////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mHEX_DIGIT = [0-9A-Fa-f]
mDIGIT = [0-9]
mBIG_SUFFIX = g | G
mFLOAT_SUFFIX = f | F
mLONG_SUFFIX = l | L
mINT_SUFFIX = i | I
mDOUBLE_SUFFIX = d | D
mEXPONENT = (e | E)("+" | "-")? [0-9] ("_"? [0-9])*

mNUM_BIN = 0 (b | B) [0-1] ("_"* [0-1])*
mNUM_HEX= 0(x | X) {mHEX_DIGIT} ("_"* {mHEX_DIGIT})*
mNUM_OCT = 0[0-7] ("_"* [0-7])*
mNUM_DEC = {mDIGIT} ("_"* {mDIGIT})*

mNUM_INT_PART = {mNUM_BIN} | {mNUM_HEX} | {mNUM_OCT} | {mNUM_DEC}
mNUM_INT = {mNUM_INT_PART} {mINT_SUFFIX}?
mNUM_LONG = {mNUM_INT_PART} {mLONG_SUFFIX}
mNUM_BIG_INT = {mNUM_INT_PART} {mBIG_SUFFIX}
mNUM_FLOAT = {mNUM_DEC} ("." {mNUM_DEC})? {mEXPONENT}? {mFLOAT_SUFFIX}
mNUM_DOUBLE = {mNUM_DEC} ("." {mNUM_DEC})? {mEXPONENT}? {mDOUBLE_SUFFIX}
mNUM_BIG_DECIMAL = {mNUM_DEC} (
  ({mEXPONENT} {mBIG_SUFFIX}?) |
  ("." {mNUM_DEC} {mEXPONENT}? {mBIG_SUFFIX}?) |
  {mBIG_SUFFIX}
)

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Identifiers /////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mLETTER = [:letter:] | "_"
mIDENT = ({mLETTER}|\$) ({mLETTER} | {mDIGIT} | \$)*
mIDENT_NOBUCKS = {mLETTER} ({mLETTER} | {mDIGIT})*

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// String & regexprs ///////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mSTRING_NL = {mONE_NL}
mSTRING_ESC = \\ [^] | \\ ({WHITE_SPACE})+ (\n|\r)

mSINGLE_QUOTED_CONTENT = {mSTRING_ESC} | [^'\\\r\n]
mSINGLE_QUOTED_LITERAL = \' {mSINGLE_QUOTED_CONTENT}* \'?

mTRIPLE_SINGLE_QUOTED_CONTENT = {mSINGLE_QUOTED_CONTENT} | {mSTRING_NL} | \'(\')?[^']
mTRIPLE_SINGLE_QUOTED_LITERAL = \'\'\' {mTRIPLE_SINGLE_QUOTED_CONTENT}* (\'{0,3} | \\?)

mDOUBLE_QUOTED_CONTENT = {mSTRING_ESC} | [^\"\\$\n\r]
mDOUBLE_QUOTED_LITERAL = \" {mDOUBLE_QUOTED_CONTENT}* \"

mTRIPLE_DOUBLE_QUOTED_CONTENT = {mDOUBLE_QUOTED_CONTENT} | {mSTRING_NL} | \"(\")?[^\"\\$]
mTRIPLE_DOUBLE_QUOTED_LITERAL = \"\"\" {mTRIPLE_DOUBLE_QUOTED_CONTENT}* \"\"\"

mSTRING_LITERAL = {mSINGLE_QUOTED_LITERAL} | {mTRIPLE_SINGLE_QUOTED_LITERAL}
mGSTRING_LITERAL = {mDOUBLE_QUOTED_LITERAL} | {mTRIPLE_DOUBLE_QUOTED_LITERAL}

%%

<YYINITIAL> {
  "}" {
    yyendstate(YYINITIAL);
    return storeToken(T_RBRACE);
  }
}

<IN_INNER_BLOCK> {
  "}" {
    yyendstate(IN_INNER_BLOCK, IN_GSTRING_DOLLAR);
    return storeToken(T_RBRACE);
  }
}

<YYINITIAL, IN_INNER_BLOCK, IN_GSTRING_DOLLAR> {
  "package"       { return storeToken(KW_PACKAGE); }
  "strictfp"      { return storeToken(KW_STRICTFP); }
  "import"        { return storeToken(KW_IMPORT); }
  "static"        { return storeToken(KW_STATIC); }
  "def"           { return storeToken(KW_DEF); }
  "class"         { return storeToken(KW_CLASS); }
  "interface"     { return storeToken(KW_INTERFACE); }
  "enum"          { return storeToken(KW_ENUM); }
  "trait"         { return storeToken(KW_TRAIT); }
  "extends"       { return storeToken(KW_EXTENDS); }
  "super"         { return storeToken(KW_SUPER); }
  "void"          { return storeToken(KW_VOID); }
  "boolean"       { return storeToken(KW_BOOLEAN); }
  "byte"          { return storeToken(KW_BYTE); }
  "char"          { return storeToken(KW_CHAR); }
  "short"         { return storeToken(KW_SHORT); }
  "int"           { return storeToken(KW_INT); }
  "float"         { return storeToken(KW_FLOAT); }
  "long"          { return storeToken(KW_LONG); }
  "double"        { return storeToken(KW_DOUBLE); }
  "as"            { return storeToken(KW_AS); }
  "private"       { return storeToken(KW_PRIVATE); }
  "abstract"      { return storeToken(KW_ABSTRACT); }
  "public"        { return storeToken(KW_PUBLIC); }
  "protected"     { return storeToken(KW_PROTECTED); }
  "transient"     { return storeToken(KW_TRANSIENT); }
  "native"        { return storeToken(KW_NATIVE); }
  "synchronized"  { return storeToken(KW_SYNCHRONIZED); }
  "volatile"      { return storeToken(KW_VOLATILE); }
  "default"       { return storeToken(KW_DEFAULT); }
  "do"            { return storeToken(KW_DO); }
  "throws"        { return storeToken(KW_THROWS); }
  "implements"    { return storeToken(KW_IMPLEMENTS); }
  "this"          { return storeToken(KW_THIS); }
  "if"            { return storeToken(KW_IF); }
  "else"          { return storeToken(KW_ELSE); }
  "while"         { return storeToken(KW_WHILE); }
  "switch"        { return storeToken(KW_SWITCH); }
  "for"           { return storeToken(KW_FOR); }
  "in"            { return storeToken(KW_IN); }
  "return"        { return storeToken(KW_RETURN); }
  "break"         { return storeToken(KW_BREAK); }
  "continue"      { return storeToken(KW_CONTINUE); }
  "throw"         { return storeToken(KW_THROW); }
  "assert"        { return storeToken(KW_ASSERT); }
  "case"          { return storeToken(KW_CASE); }
  "try"           { return storeToken(KW_TRY); }
  "finally"       { return storeToken(KW_FINALLY); }
  "catch"         { return storeToken(KW_CATCH); }
  "instanceof"    { return storeToken(KW_INSTANCEOF); }
  "new"           { return storeToken(KW_NEW); }
  "true"          { return storeToken(KW_TRUE); }
  "false"         { return storeToken(KW_FALSE); }
  "null"          { return storeToken(KW_NULL); }
  "final"         { return storeToken(KW_FINAL); }
}

<NLS_AFTER_COMMENT> {
  {mSL_COMMENT}                             { return SL_COMMENT; }
  {mML_COMMENT}                             { return ML_COMMENT; }
  {mDOC_COMMENT}                            { return GROOVY_DOC_COMMENT; }

  ({mNLS}|{WHITE_SPACE})+                   { return TokenType.WHITE_SPACE; }

  [^] {
    yypushback(1);
    yyendstate(NLS_AFTER_COMMENT);
  }
}

<NLS_AFTER_LBRACE> {
  ({mNLS}|{WHITE_SPACE})+                   { return TokenType.WHITE_SPACE; }
  [^] {
    yypushback(1);
    yyendstate(NLS_AFTER_LBRACE);
  }
}

<NLS_AFTER_NLS>{
  ({mNLS}|{WHITE_SPACE})+                   { return TokenType.WHITE_SPACE; }

  [^] {
    yypushback(1);
    yyendstate(NLS_AFTER_NLS);
    yybeginstate(NLS_AFTER_COMMENT);
  }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////  Groovy Strings ///////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

<IN_SINGLE_GSTRING> {
  \" {
    yyendstate(IN_SINGLE_GSTRING);
    return storeToken(GSTRING_END);
  }

  [^$\"\\\n\r]+         { return storeToken(GSTRING_CONTENT); }
  {mSTRING_ESC}         { return storeToken(GSTRING_CONTENT); }
  \\. | \\              { return storeToken(GSTRING_CONTENT); }

  {mNLS} {
    resetState();
    yybeginstate(NLS_AFTER_NLS);
    return storeToken(NL);
  }

  "$" {
    yybeginstate(IN_GSTRING_DOLLAR);
    return storeToken(T_DOLLAR);
  }
}

<IN_TRIPLE_GSTRING> {
  \"\"\" {
    yyendstate(IN_TRIPLE_GSTRING);
    return storeToken(GSTRING_END);
  }

  [^$\"\\]+             { return storeToken(GSTRING_CONTENT); }
  \\. | \\ | \" | \"\"  { return storeToken(GSTRING_CONTENT); }

  "$" {
    yybeginstate(IN_GSTRING_DOLLAR);
    return storeToken(T_DOLLAR);
  }
}

<IN_SLASHY_STRING> {
  "/" {
    yyendstate(IN_SLASHY_STRING);
    return storeToken(SLASHY_END);
  }

  [^$/\\]+              { return storeToken(SLASHY_CONTENT); }
  \\"/" | \\            { return storeToken(SLASHY_CONTENT); }
  "$" /[^_[:letter:]{]  { return storeToken(SLASHY_CONTENT); }

  "$" {
    yybeginstate(IN_GSTRING_DOLLAR);
    return storeToken(T_DOLLAR);
  }
}

<IN_DOLLAR_SLASH_STRING> {
  "/$" {
    yyendstate(IN_DOLLAR_SLASH_STRING);
    return storeToken(DOLLAR_SLASHY_END);
  }

  [^$/]+                { return storeToken(DOLLAR_SLASHY_CONTENT); }
  "$$" | "$/" | "/"     { return storeToken(DOLLAR_SLASHY_CONTENT); }
  "$" /[^_[:letter:]{]  { return storeToken(DOLLAR_SLASHY_CONTENT); }

  "$" {
    yybeginstate(IN_GSTRING_DOLLAR);
    return storeToken(T_DOLLAR);
  }
}

<IN_GSTRING_DOLLAR> {
  {mIDENT_NOBUCKS} {
    yybeginstate(IN_GSTRING_DOT);
    return storeToken(IDENTIFIER);
  }

  "{" {
    yybeginstate(IN_INNER_BLOCK, NLS_AFTER_LBRACE);
    return storeToken(T_LBRACE);
  }

  [^] {
    yypushback(1);
    yyendstate(IN_GSTRING_DOLLAR);
  }
}

<IN_GSTRING_DOT> {
  "." /{mIDENT_NOBUCKS} {
    yybeginstate(IN_GSTRING_DOT_IDENT);
    return storeToken(T_DOT);
  }
  [^] {
    yypushback(1);
    yyendstate(IN_GSTRING_DOT);
  }
}

<IN_GSTRING_DOT_IDENT> {
  {mIDENT_NOBUCKS} {
    yybeginstate(IN_GSTRING_DOT);
    return storeToken(IDENTIFIER);
  }
  [^] {
    yypushback(1);
    yyendstate(IN_GSTRING_DOT_IDENT);
  }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// White spaces & NewLines //////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{WHITE_SPACE}                             { return TokenType.WHITE_SPACE; }
{mNLS}                                    {
                                            yybeginstate(NLS_AFTER_NLS);
                                            return isWithinBraces() ? TokenType.WHITE_SPACE : storeToken(NL);
                                          }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////Comments //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mSH_COMMENT}                             { return storeToken(SH_COMMENT); }
{mSL_COMMENT}                             { return storeToken(SL_COMMENT); }
{mML_COMMENT}                             { return storeToken(ML_COMMENT); }
{mDOC_COMMENT}                            { return storeToken(GROOVY_DOC_COMMENT); }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Integers and floats //////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mNUM_INT}                                { return storeToken(NUM_INT); }
{mNUM_BIG_INT}                            { return storeToken(NUM_BIG_INT); }
{mNUM_BIG_DECIMAL}                        { return storeToken(NUM_BIG_DECIMAL); }
{mNUM_FLOAT}                              { return storeToken(NUM_FLOAT); }
{mNUM_DOUBLE}                             { return storeToken(NUM_DOUBLE); }
{mNUM_LONG}                               { return storeToken(NUM_LONG); }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Strings & regular expressions ////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mSTRING_LITERAL}                         { return storeToken(STR_SQ); }
{mGSTRING_LITERAL}                        { return storeToken(STR_DQ); }
\"\"\"                                    {
                                            yybeginstate(IN_TRIPLE_GSTRING);
                                            return storeToken(GSTRING_BEGIN);
                                          }
\"                                        {
                                            yybeginstate(IN_SINGLE_GSTRING);
                                            return storeToken(GSTRING_BEGIN);
                                          }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      Identifiers      ////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mIDENT}                                  { return storeToken(IDENTIFIER); }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Reserved shorthands //////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

<DIVISION_EXPECTED> {
  {WHITE_SPACE} {
    return TokenType.WHITE_SPACE;
  }
  "/"/[^/*=] {
    yyendstate(DIVISION_EXPECTED);
    return storeToken(T_DIV);
  }
  "$/" {
    yypushback(1);
    yyendstate(DIVISION_EXPECTED);
    return storeToken(T_DOLLAR);
  }
  [^] {
    yypushback(1);
    yyendstate(DIVISION_EXPECTED);
  }
}

"/"                                       {
                                            yybeginstate(IN_SLASHY_STRING);
                                            return storeToken(SLASHY_BEGIN);
                                          }
"$/"                                      {
                                            yybeginstate(IN_DOLLAR_SLASH_STRING);
                                            return storeToken(DOLLAR_SLASHY_BEGIN);
                                          }
"{"                                       {
                                            yybeginstate(YYINITIAL, NLS_AFTER_LBRACE);
                                            return storeToken(T_LBRACE);
                                          }
"?"                                       { return storeToken(T_Q); }
"/="                                      { return storeToken(T_DIV_ASSIGN); }
"("                                       { return storeToken(T_LPAREN); }
")"                                       { return storeToken(T_RPAREN); }
"["                                       { return storeToken(T_LBRACK); }
"]"                                       { return storeToken(T_RBRACK); }
":"                                       { return storeToken(T_COLON); }
","                                       { return storeToken(T_COMMA); }
"."                                       { return storeToken(T_DOT); }
"="                                       { return storeToken(T_ASSIGN); }
"<=>"                                     { return storeToken(T_COMPARE); }
"==="                                     { return storeToken(T_ID); }
"=="                                      { return storeToken(T_EQ); }
"!"                                       { return storeToken(T_NOT); }
"~"                                       { return storeToken(T_BNOT); }
"!=="                                     { return storeToken(T_NID); }
"!="                                      { return storeToken(T_NEQ); }
"+"                                       { return storeToken(T_PLUS); }
"+="                                      { return storeToken(T_PLUS_ASSIGN); }
"++"                                      { return storeToken(T_INC); }
"-"                                       { return storeToken(T_MINUS); }
"-="                                      { return storeToken(T_MINUS_ASSIGN); }
"--"                                      { return storeToken(T_DEC); }
"*"                                       { return storeToken(T_STAR); }
"*="                                      { return storeToken(T_STAR_ASSIGN); }
"%"                                       { return storeToken(T_REM); }
"%="                                      { return storeToken(T_REM_ASSIGN); }
">>="                                     { return storeToken(T_RSH_ASSIGN); }
">>>="                                    { return storeToken(T_RSHU_ASSIGN); }
">="                                      { return storeToken(T_GE); }
">"                                       { return storeToken(T_GT); }
"<<="                                     { return storeToken(T_LSH_ASSIGN); }
"<="                                      { return storeToken(T_LE); }
"?:"                                      { return storeToken(T_ELVIS); }
"?="                                      { return storeToken(T_ELVIS_ASSIGN); }
"<"                                       { return storeToken(T_LT); }
"^"                                       { return storeToken(T_XOR); }
"^="                                      { return storeToken(T_XOR_ASSIGN); }
"|"                                       { return storeToken(T_BOR); }
"|="                                      { return storeToken(T_BOR_ASSIGN); }
"||"                                      { return storeToken(T_LOR); }
"&"                                       { return storeToken(T_BAND); }
"&="                                      { return storeToken(T_BAND_ASSIGN); }
"&&"                                      { return storeToken(T_LAND); }
";"                                       { return storeToken(T_SEMI); }
".."                                      { return storeToken(T_RANGE); }
"..<"                                     { return storeToken(T_RANGE_EX); }
"..."                                     { return storeToken(T_ELLIPSIS); }
"*."                                      { return storeToken(T_SPREAD_DOT); }
"?."                                      { return storeToken(T_SAFE_DOT); }
".&"                                      { return storeToken(T_METHOD_CLOSURE); }
"=~"                                      { return storeToken(T_REGEX_FIND); }
"==~"                                     { return storeToken(T_REGEX_MATCH); }
"**"                                      { return storeToken(T_POW); }
"**="                                     { return storeToken(T_POW_ASSIGN); }
"->"                                      { return storeToken(T_ARROW); }
"@"                                       { return storeToken(T_AT); }
.                                         { return T_WRONG; }
