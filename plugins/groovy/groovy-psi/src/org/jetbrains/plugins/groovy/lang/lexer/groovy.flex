// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.Stack;
import static org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes.*;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;

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
    return storeToken(mRCURLY);
  }
}

<IN_INNER_BLOCK> {
  "}" {
    yyendstate(IN_INNER_BLOCK, IN_GSTRING_DOLLAR);
    return storeToken(mRCURLY);
  }
}

<YYINITIAL, IN_INNER_BLOCK, IN_GSTRING_DOLLAR> {
  "package"       { return storeToken(kPACKAGE); }
  "strictfp"      { return storeToken(kSTRICTFP); }
  "import"        { return storeToken(kIMPORT); }
  "static"        { return storeToken(kSTATIC); }
  "def"           { return storeToken(kDEF); }
  "class"         { return storeToken(kCLASS); }
  "interface"     { return storeToken(kINTERFACE); }
  "enum"          { return storeToken(kENUM); }
  "trait"         { return storeToken(kTRAIT); }
  "extends"       { return storeToken(kEXTENDS); }
  "super"         { return storeToken(kSUPER); }
  "void"          { return storeToken(kVOID); }
  "boolean"       { return storeToken(kBOOLEAN); }
  "byte"          { return storeToken(kBYTE); }
  "char"          { return storeToken(kCHAR); }
  "short"         { return storeToken(kSHORT); }
  "int"           { return storeToken(kINT); }
  "float"         { return storeToken(kFLOAT); }
  "long"          { return storeToken(kLONG); }
  "double"        { return storeToken(kDOUBLE); }
  "as"            { return storeToken(kAS); }
  "private"       { return storeToken(kPRIVATE); }
  "abstract"      { return storeToken(kABSTRACT); }
  "public"        { return storeToken(kPUBLIC); }
  "protected"     { return storeToken(kPROTECTED); }
  "transient"     { return storeToken(kTRANSIENT); }
  "native"        { return storeToken(kNATIVE); }
  "synchronized"  { return storeToken(kSYNCHRONIZED); }
  "volatile"      { return storeToken(kVOLATILE); }
  "default"       { return storeToken(kDEFAULT); }
  "do"            { return storeToken(kDO); }
  "throws"        { return storeToken(kTHROWS); }
  "implements"    { return storeToken(kIMPLEMENTS); }
  "this"          { return storeToken(kTHIS); }
  "if"            { return storeToken(kIF); }
  "else"          { return storeToken(kELSE); }
  "while"         { return storeToken(kWHILE); }
  "switch"        { return storeToken(kSWITCH); }
  "for"           { return storeToken(kFOR); }
  "in"            { return storeToken(kIN); }
  "return"        { return storeToken(kRETURN); }
  "break"         { return storeToken(kBREAK); }
  "continue"      { return storeToken(kCONTINUE); }
  "throw"         { return storeToken(kTHROW); }
  "assert"        { return storeToken(kASSERT); }
  "case"          { return storeToken(kCASE); }
  "try"           { return storeToken(kTRY); }
  "finally"       { return storeToken(kFINALLY); }
  "catch"         { return storeToken(kCATCH); }
  "instanceof"    { return storeToken(kINSTANCEOF); }
  "new"           { return storeToken(kNEW); }
  "true"          { return storeToken(kTRUE); }
  "false"         { return storeToken(kFALSE); }
  "null"          { return storeToken(kNULL); }
  "final"         { return storeToken(kFINAL); }
}

<NLS_AFTER_COMMENT> {
  {mSL_COMMENT}                             { return mSL_COMMENT; }
  {mML_COMMENT}                             { return mML_COMMENT; }
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
    return storeToken(mGSTRING_END);
  }

  [^$\"\\\n\r]+         { return storeToken(mGSTRING_CONTENT); }
  {mSTRING_ESC}         { return storeToken(mGSTRING_CONTENT); }
  \\. | \\              { return storeToken(mGSTRING_CONTENT); }

  {mNLS} {
    resetState();
    yybeginstate(NLS_AFTER_NLS);
    return storeToken(mNLS);
  }

  "$" {
    yybeginstate(IN_GSTRING_DOLLAR);
    return storeToken(mDOLLAR);
  }
}

<IN_TRIPLE_GSTRING> {
  \"\"\" {
    yyendstate(IN_TRIPLE_GSTRING);
    return storeToken(mGSTRING_END);
  }

  [^$\"\\]+             { return storeToken(mGSTRING_CONTENT); }
  \\. | \\ | \" | \"\"  { return storeToken(mGSTRING_CONTENT); }

  "$" {
    yybeginstate(IN_GSTRING_DOLLAR);
    return storeToken(mDOLLAR);
  }
}

<IN_SLASHY_STRING> {
  "/" {
    yyendstate(IN_SLASHY_STRING);
    return storeToken(mREGEX_END);
  }

  [^$/\\]+              { return storeToken(mREGEX_CONTENT); }
  \\"/" | \\            { return storeToken(mREGEX_CONTENT); }
  "$" /[^_[:letter:]{]  { return storeToken(mREGEX_CONTENT); }

  "$" {
    yybeginstate(IN_GSTRING_DOLLAR);
    return storeToken(mDOLLAR);
  }
}

<IN_DOLLAR_SLASH_STRING> {
  "/$" {
    yyendstate(IN_DOLLAR_SLASH_STRING);
    return storeToken(mDOLLAR_SLASH_REGEX_END);
  }

  [^$/]+                { return storeToken(mDOLLAR_SLASH_REGEX_CONTENT); }
  "$$" | "$/" | "/"     { return storeToken(mDOLLAR_SLASH_REGEX_CONTENT); }
  "$" /[^_[:letter:]{]  { return storeToken(mDOLLAR_SLASH_REGEX_CONTENT); }

  "$" {
    yybeginstate(IN_GSTRING_DOLLAR);
    return storeToken(mDOLLAR);
  }
}

<IN_GSTRING_DOLLAR> {
  {mIDENT_NOBUCKS} {
    yybeginstate(IN_GSTRING_DOT);
    return storeToken(mIDENT);
  }

  "{" {
    yybeginstate(IN_INNER_BLOCK, NLS_AFTER_LBRACE);
    return storeToken(mLCURLY);
  }

  [^] {
    yypushback(1);
    yyendstate(IN_GSTRING_DOLLAR);
  }
}

<IN_GSTRING_DOT> {
  "." /{mIDENT_NOBUCKS} {
    yybeginstate(IN_GSTRING_DOT_IDENT);
    return storeToken(mDOT);
  }
  [^] {
    yypushback(1);
    yyendstate(IN_GSTRING_DOT);
  }
}

<IN_GSTRING_DOT_IDENT> {
  {mIDENT_NOBUCKS} {
    yybeginstate(IN_GSTRING_DOT);
    return storeToken(mIDENT);
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
                                            return isWithinBraces() ? TokenType.WHITE_SPACE : storeToken(mNLS);
                                          }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////Comments //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mSH_COMMENT}                             { return storeToken(mSH_COMMENT); }
{mSL_COMMENT}                             { return storeToken(mSL_COMMENT); }
{mML_COMMENT}                             { return storeToken(mML_COMMENT); }
{mDOC_COMMENT}                            { return storeToken(GROOVY_DOC_COMMENT); }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Integers and floats //////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mNUM_INT}                                { return storeToken(mNUM_INT); }
{mNUM_BIG_INT}                            { return storeToken(mNUM_BIG_INT); }
{mNUM_BIG_DECIMAL}                        { return storeToken(mNUM_BIG_DECIMAL); }
{mNUM_FLOAT}                              { return storeToken(mNUM_FLOAT); }
{mNUM_DOUBLE}                             { return storeToken(mNUM_DOUBLE); }
{mNUM_LONG}                               { return storeToken(mNUM_LONG); }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Strings & regular expressions ////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mSTRING_LITERAL}                         { return storeToken(mSTRING_LITERAL); }
{mGSTRING_LITERAL}                        { return storeToken(mGSTRING_LITERAL); }
\"\"\"                                    {
                                            yybeginstate(IN_TRIPLE_GSTRING);
                                            return storeToken(mGSTRING_BEGIN);
                                          }
\"                                        {
                                            yybeginstate(IN_SINGLE_GSTRING);
                                            return storeToken(mGSTRING_BEGIN);
                                          }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      Identifiers      ////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mIDENT}                                  { return storeToken(mIDENT); }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Reserved shorthands //////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

<DIVISION_EXPECTED> {
  {WHITE_SPACE} {
    return TokenType.WHITE_SPACE;
  }
  "/"/[^/*=] {
    yyendstate(DIVISION_EXPECTED);
    return storeToken(mDIV);
  }
  "$/" {
    yypushback(1);
    yyendstate(DIVISION_EXPECTED);
    return storeToken(mDOLLAR);
  }
  [^] {
    yypushback(1);
    yyendstate(DIVISION_EXPECTED);
  }
}

"/"                                       {
                                            yybeginstate(IN_SLASHY_STRING);
                                            return storeToken(mREGEX_BEGIN);
                                          }
"$/"                                      {
                                            yybeginstate(IN_DOLLAR_SLASH_STRING);
                                            return storeToken(mDOLLAR_SLASH_REGEX_BEGIN);
                                          }
"{"                                       {
                                            yybeginstate(YYINITIAL, NLS_AFTER_LBRACE);
                                            return storeToken(mLCURLY);
                                          }
"?"                                       { return storeToken(mQUESTION); }
"/="                                      { return storeToken(mDIV_ASSIGN); }
"("                                       { return storeToken(mLPAREN); }
")"                                       { return storeToken(mRPAREN); }
"["                                       { return storeToken(mLBRACK); }
"]"                                       { return storeToken(mRBRACK); }
":"                                       { return storeToken(mCOLON); }
","                                       { return storeToken(mCOMMA); }
"."                                       { return storeToken(mDOT); }
"="                                       { return storeToken(mASSIGN); }
"<=>"                                     { return storeToken(mCOMPARE_TO); }
"=="|"==="                                { return storeToken(mEQUAL); }
"!"                                       { return storeToken(mLNOT); }
"~"                                       { return storeToken(mBNOT); }
"!="|"!=="                                { return storeToken(mNOT_EQUAL); }
"+"                                       { return storeToken(mPLUS); }
"+="                                      { return storeToken(mPLUS_ASSIGN); }
"++"                                      { return storeToken(mINC); }
"-"                                       { return storeToken(mMINUS); }
"-="                                      { return storeToken(mMINUS_ASSIGN); }
"--"                                      { return storeToken(mDEC); }
"*"                                       { return storeToken(mSTAR); }
"*="                                      { return storeToken(mSTAR_ASSIGN); }
"%"                                       { return storeToken(mMOD); }
"%="                                      { return storeToken(mMOD_ASSIGN); }
">>="                                     { return storeToken(mSR_ASSIGN); }
">>>="                                    { return storeToken(mBSR_ASSIGN); }
">="                                      { return storeToken(mGE); }
">"                                       { return storeToken(mGT); }
"<<="                                     { return storeToken(mSL_ASSIGN); }
"<="                                      { return storeToken(mLE); }
"?:"                                      { return storeToken(mELVIS); }
"<"                                       { return storeToken(mLT); }
"^"                                       { return storeToken(mBXOR); }
"^="                                      { return storeToken(mBXOR_ASSIGN); }
"|"                                       { return storeToken(mBOR); }
"|="                                      { return storeToken(mBOR_ASSIGN); }
"||"                                      { return storeToken(mLOR); }
"&"                                       { return storeToken(mBAND); }
"&="                                      { return storeToken(mBAND_ASSIGN); }
"&&"                                      { return storeToken(mLAND); }
";"                                       { return storeToken(mSEMI); }
".."                                      { return storeToken(mRANGE_INCLUSIVE); }
"..<"                                     { return storeToken(mRANGE_EXCLUSIVE); }
"..."                                     { return storeToken(mTRIPLE_DOT); }
"*."                                      { return storeToken(mSPREAD_DOT); }
"?."                                      { return storeToken(mOPTIONAL_DOT); }
".&"                                      { return storeToken(mMEMBER_POINTER); }
"=~"                                      { return storeToken(mREGEX_FIND); }
"==~"                                     { return storeToken(mREGEX_MATCH); }
"**"                                      { return storeToken(mSTAR_STAR); }
"**="                                     { return storeToken(mSTAR_STAR_ASSIGN); }
"->"                                      { return storeToken(mCLOSABLE_BLOCK_OP); }
"@"                                       { return storeToken(mAT); }
.                                         { return mWRONG; }
