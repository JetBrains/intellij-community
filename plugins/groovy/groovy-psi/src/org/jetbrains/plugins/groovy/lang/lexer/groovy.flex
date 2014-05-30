/*
 * Copyright 2000-2014 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.Stack;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;

%%

%class _GroovyLexer
%implements FlexLexer
%unicode
%public

%function advance
%type IElementType

%eof{ return;
%eof}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////// User code //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%{

  private Stack <IElementType> gStringStack = new Stack<IElementType>();
  private Stack <IElementType> blockStack = new Stack<IElementType>();

  private int afterComment = YYINITIAL;

  private void clearStacks(){
    gStringStack.clear();
    blockStack.clear();
  }

  private Stack<IElementType> braceCount = new Stack <IElementType>();

%}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// NewLines and spaces /////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mONE_NL = \r | \n | \r\n                                    // NewLines
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
/////////////////////      integers and floats     /////////////////////////////////////////////////////////////////////
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

// Integer
mNUM_INT = {mNUM_INT_PART} {mINT_SUFFIX}?

// Long
mNUM_LONG = {mNUM_INT_PART} {mLONG_SUFFIX}

// BigInteger
mNUM_BIG_INT = {mNUM_INT_PART} {mBIG_SUFFIX}

//Float
mNUM_FLOAT = {mNUM_DEC} ("." {mNUM_DEC})? {mEXPONENT}? {mFLOAT_SUFFIX}

// Double
mNUM_DOUBLE = {mNUM_DEC} ("." {mNUM_DEC})? {mEXPONENT}? {mDOUBLE_SUFFIX}


// BigDecimal
mNUM_BIG_DECIMAL = {mNUM_DEC} (
  ({mEXPONENT} {mBIG_SUFFIX}?) |
  ("." {mNUM_DEC} {mEXPONENT}? {mBIG_SUFFIX}?) |
  {mBIG_SUFFIX}
)

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      identifiers      ////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mLETTER = [:letter:] | "_"

mIDENT = ({mLETTER}|\$) ({mLETTER} | {mDIGIT} | \$)*
mIDENT_NOBUCKS = {mLETTER} ({mLETTER} | {mDIGIT})*


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// String & regexprs ///////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mSTRING_NL = {mONE_NL}
mSTRING_ESC = \\ [^] | \\ ({WHITE_SPACE})+ (\n|\r)
mREGEX_ESC = \\ "/"
| "\\""u"{mHEX_DIGIT}{4}
| "\\" [0..3] ([0..7] ([0..7])?)?
| "\\" [4..7] ([0..7])?
| "\\" ({WHITE_SPACE})* {mONE_NL}

/// Regexes ////////////////////////////////////////////////////////////////

mREGEX_CONTENT = ({mREGEX_ESC} | [^"/""$"])+

mDOLLAR_SLASH_REGEX_CONTENT = ([^\/\$] | \$\$ | \$\/ | \/[^\/\$] )+

////////////////////////////////////////////////////////////////////////////

mSINGLE_QUOTED_STRING_BEGIN = "\'" ( {mSTRING_ESC}
    | "\""
    | [^\\\'\r\n]
    | "$")*
mSINGLE_QUOTED_STRING = {mSINGLE_QUOTED_STRING_BEGIN} \'
mTRIPLE_QUOTED_STRING = "\'\'\'" ({mSTRING_ESC}
    | \"
    | "$"
    | [^\']
    | {mSTRING_NL}
    | \'(\')?[^\'] )* (\'\'\' | \\)?

mSTRING_LITERAL = {mTRIPLE_QUOTED_STRING}
    | {mSINGLE_QUOTED_STRING}


// Single-double-quoted GStrings
mGSTRING_SINGLE_CONTENT = ({mSTRING_ESC}
    | [^\\\"\r\n"$"]
    | "\'" )+

// Triple-double-quoted GStrings
mGSTRING_TRIPLE_CONTENT = ({mSTRING_ESC}
    | \'
    | \" (\")? [^\""$"\\]
    | [^\\\""$"]
    | {mSTRING_NL})+


mGSTRING_TRIPLE_CTOR_END = {mGSTRING_TRIPLE_CONTENT} \"\"\"


mGSTRING_LITERAL = \"\"
    | \" ([^\\\"\n\r"$"] | {mSTRING_ESC})? {mGSTRING_SINGLE_CONTENT} \"
    | \"\"\" {mGSTRING_TRIPLE_CTOR_END}



////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////  states ///////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%xstate IN_SINGLE_GSTRING_DOLLAR
%xstate IN_TRIPLE_GSTRING_DOLLAR
%xstate IN_SINGLE_GSTRING
%xstate IN_TRIPLE_GSTRING
%xstate IN_SINGLE_IDENT
%xstate IN_SINGLE_DOT
%xstate IN_TRIPLE_IDENT
%xstate IN_TRIPLE_DOT
%xstate IN_TRIPLE_NLS

%state IN_INNER_BLOCK

%xstate WAIT_FOR_REGEX
%xstate IN_REGEX_DOLLAR
%xstate IN_REGEX
%xstate IN_REGEX_IDENT
%xstate IN_REGEX_DOT
%xstate IN_DOLLAR_SLASH_REGEX_DOLLAR
%xstate IN_DOLLAR_SLASH_REGEX
%xstate IN_DOLLAR_SLASH_REGEX_IDENT
%xstate IN_DOLLAR_SLASH_REGEX_DOT

// Not to separate NewLine sequence by comments
%xstate NLS_AFTER_COMMENT
// Special hacks for IDEA formatter
%xstate NLS_AFTER_LBRACE
%xstate NLS_AFTER_NLS

%state BRACE_COUNT

%%
<NLS_AFTER_COMMENT>{

  {mSL_COMMENT}                             {  return GroovyTokenTypes.mSL_COMMENT; }
  {mML_COMMENT}                             {  return GroovyTokenTypes.mML_COMMENT; }
  {mDOC_COMMENT}                            {  return GroovyDocElementTypes.GROOVY_DOC_COMMENT; }

  ({mNLS}|{WHITE_SPACE})+                   {  return TokenType.WHITE_SPACE; }

  [^]                                       { yypushback(1);
                                              yybegin(afterComment);  }
}
<NLS_AFTER_LBRACE>{

  ({mNLS}|{WHITE_SPACE})+                   { return TokenType.WHITE_SPACE; }

  [^]                                       { yypushback(1);
                                              yybegin(WAIT_FOR_REGEX);  }
}
<NLS_AFTER_NLS>{

  ({mNLS}|{WHITE_SPACE})+                   { return TokenType.WHITE_SPACE; }

  [^]                                       { yypushback(1);
                                              yybegin(NLS_AFTER_COMMENT);  }
}


// Single double-quoted GString
<IN_SINGLE_IDENT>{
  {mIDENT_NOBUCKS}                        {  yybegin(IN_SINGLE_DOT);
                                             return GroovyTokenTypes.mIDENT;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_SINGLE_GSTRING);  }
}
<IN_SINGLE_DOT>{
  "." /{mIDENT_NOBUCKS}                   {  yybegin(IN_SINGLE_IDENT);
                                             return GroovyTokenTypes.mDOT;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_SINGLE_GSTRING);  }
}

<IN_SINGLE_GSTRING_DOLLAR> {

  "package"                               {  return ( GroovyTokenTypes.kPACKAGE );  }
  "strictfp"                              {  return ( GroovyTokenTypes.kSTRICTFP );  }
  "import"                                {  return ( GroovyTokenTypes.kIMPORT );  }
  "static"                                {  return ( GroovyTokenTypes.kSTATIC );  }
  "def"                                   {  return ( GroovyTokenTypes.kDEF );  }
  "class"                                 {  return ( GroovyTokenTypes.kCLASS );  }
  "interface"                             {  return ( GroovyTokenTypes.kINTERFACE );  }
  "enum"                                  {  return ( GroovyTokenTypes.kENUM );  }
  "trait"                                 {  return ( GroovyTokenTypes.kTRAIT );  }
  "extends"                               {  return ( GroovyTokenTypes.kEXTENDS );  }
  "super"                                 {  return ( GroovyTokenTypes.kSUPER );  }
  "void"                                  {  return ( GroovyTokenTypes.kVOID );  }
  "boolean"                               {  return ( GroovyTokenTypes.kBOOLEAN );  }
  "byte"                                  {  return ( GroovyTokenTypes.kBYTE );  }
  "char"                                  {  return ( GroovyTokenTypes.kCHAR );  }
  "short"                                 {  return ( GroovyTokenTypes.kSHORT );  }
  "int"                                   {  return ( GroovyTokenTypes.kINT );  }
  "float"                                 {  return ( GroovyTokenTypes.kFLOAT );  }
  "long"                                  {  return ( GroovyTokenTypes.kLONG );  }
  "double"                                {  return ( GroovyTokenTypes.kDOUBLE );  }
  "as"                                    {  return ( GroovyTokenTypes.kAS );  }
  "private"                               {  return ( GroovyTokenTypes.kPRIVATE );  }
  "abstract"                              {  return ( GroovyTokenTypes.kABSTRACT );  }
  "public"                                {  return ( GroovyTokenTypes.kPUBLIC );  }
  "protected"                             {  return ( GroovyTokenTypes.kPROTECTED );  }
  "transient"                             {  return ( GroovyTokenTypes.kTRANSIENT );  }
  "native"                                {  return ( GroovyTokenTypes.kNATIVE );  }
  "synchronized"                          {  return ( GroovyTokenTypes.kSYNCHRONIZED );  }
  "volatile"                              {  return ( GroovyTokenTypes.kVOLATILE );  }
  "default"                               {  return ( GroovyTokenTypes.kDEFAULT );  }
  "do"                                    {  return ( GroovyTokenTypes.kDO );  }
  "throws"                                {  return ( GroovyTokenTypes.kTHROWS );  }
  "implements"                            {  return ( GroovyTokenTypes.kIMPLEMENTS );  }
  "this"                                  {  return ( GroovyTokenTypes.kTHIS );  }
  "if"                                    {  return ( GroovyTokenTypes.kIF );  }
  "else"                                  {  return ( GroovyTokenTypes.kELSE );  }
  "while"                                 {  return ( GroovyTokenTypes.kWHILE );  }
  "switch"                                {  return ( GroovyTokenTypes.kSWITCH );  }
  "for"                                   {  return ( GroovyTokenTypes.kFOR );  }
  "in"                                    {  return ( GroovyTokenTypes.kIN );  }
  "return"                                {  return ( GroovyTokenTypes.kRETURN );  }
  "break"                                 {  return ( GroovyTokenTypes.kBREAK );  }
  "continue"                              {  return ( GroovyTokenTypes.kCONTINUE );  }
  "throw"                                 {  return ( GroovyTokenTypes.kTHROW );  }
  "assert"                                {  return ( GroovyTokenTypes.kASSERT );  }
  "case"                                  {  return ( GroovyTokenTypes.kCASE );  }
  "try"                                   {  return ( GroovyTokenTypes.kTRY );  }
  "finally"                               {  return ( GroovyTokenTypes.kFINALLY );  }
  "catch"                                 {  return ( GroovyTokenTypes.kCATCH );  }
  "instanceof"                            {  return ( GroovyTokenTypes.kINSTANCEOF );  }
  "new"                                   {  return ( GroovyTokenTypes.kNEW );  }
  "true"                                  {  return ( GroovyTokenTypes.kTRUE );  }
  "false"                                 {  return ( GroovyTokenTypes.kFALSE );  }
  "null"                                  {  return ( GroovyTokenTypes.kNULL );  }
  "final"                                 {  return ( GroovyTokenTypes.kFINAL );  }

  {mIDENT_NOBUCKS}                        {  yybegin(IN_SINGLE_DOT);
                                             return GroovyTokenTypes.mIDENT; }
  "{"                                     {  blockStack.push(GroovyTokenTypes.mLPAREN);
                                             braceCount.push(GroovyTokenTypes.mLCURLY);
                                             yybegin(NLS_AFTER_LBRACE);
                                             return GroovyTokenTypes.mLCURLY; }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_SINGLE_GSTRING); }
}

<IN_SINGLE_GSTRING> {
  {mGSTRING_SINGLE_CONTENT} (\\)?         {  return GroovyTokenTypes.mGSTRING_CONTENT; }
  \\                                      {  return GroovyTokenTypes.mGSTRING_CONTENT; }

  \"                                      {  if (!gStringStack.isEmpty()) {
                                               gStringStack.pop();
                                             }
                                             if (blockStack.isEmpty()){
                                               yybegin(YYINITIAL);
                                             } else {
                                               yybegin(IN_INNER_BLOCK);
                                             }
                                             return GroovyTokenTypes.mGSTRING_END; }
  "$"                                     {  yybegin(IN_SINGLE_GSTRING_DOLLAR);
                                             return GroovyTokenTypes.mDOLLAR;
                                          }
  {mNLS}                                  {  clearStacks();
                                             yybegin(NLS_AFTER_NLS);
                                             afterComment = YYINITIAL;
                                             return GroovyTokenTypes.mNLS; }
}

<IN_INNER_BLOCK>{
  "{"                                     {  blockStack.push(GroovyTokenTypes.mLCURLY);
                                             braceCount.push(GroovyTokenTypes.mLCURLY);
                                             yybegin(NLS_AFTER_LBRACE);
                                             return (GroovyTokenTypes.mLCURLY);  }

  "}"                                     {  if (!blockStack.isEmpty()) {
                                               IElementType br = blockStack.pop();
                                               if (br.equals(GroovyTokenTypes.mLPAREN)) yybegin(IN_SINGLE_GSTRING);
                                               if (br.equals(GroovyTokenTypes.mLBRACK)) yybegin(IN_TRIPLE_GSTRING);
                                               if (br.equals(GroovyTokenTypes.mDIV)) yybegin(IN_REGEX);
                                               if (br.equals(GroovyTokenTypes.mDOLLAR)) yybegin(IN_DOLLAR_SLASH_REGEX);
                                             }
                                             while (!braceCount.isEmpty() && GroovyTokenTypes.mLCURLY != braceCount.peek()) {
                                               braceCount.pop();
                                             }
                                             if (!braceCount.isEmpty() && GroovyTokenTypes.mLCURLY == braceCount.peek()) {
                                               braceCount.pop();
                                             }
                                             return GroovyTokenTypes.mRCURLY; }
}

// Triple double-quoted GString
<IN_TRIPLE_IDENT>{
  {mIDENT_NOBUCKS}                        {  yybegin(IN_TRIPLE_DOT);
                                             return GroovyTokenTypes.mIDENT;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_TRIPLE_GSTRING);  }
}
<IN_TRIPLE_DOT>{
  "." /{mIDENT_NOBUCKS}                   {  yybegin(IN_TRIPLE_NLS);
                                             return GroovyTokenTypes.mDOT;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_TRIPLE_GSTRING);  }
}
<IN_TRIPLE_NLS>{
  {mNLS}                                  {  yybegin(NLS_AFTER_NLS);
                                             afterComment = IN_TRIPLE_IDENT;
                                             return GroovyTokenTypes.mNLS;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_TRIPLE_IDENT);  }

}

<IN_TRIPLE_GSTRING_DOLLAR> {
  "package"                               {  return ( GroovyTokenTypes.kPACKAGE );  }
  "strictfp"                              {  return ( GroovyTokenTypes.kSTRICTFP );  }
  "import"                                {  return ( GroovyTokenTypes.kIMPORT );  }
  "static"                                {  return ( GroovyTokenTypes.kSTATIC );  }
  "def"                                   {  return ( GroovyTokenTypes.kDEF );  }
  "class"                                 {  return ( GroovyTokenTypes.kCLASS );  }
  "interface"                             {  return ( GroovyTokenTypes.kINTERFACE );  }
  "enum"                                  {  return ( GroovyTokenTypes.kENUM );  }
  "trait"                                 {  return ( GroovyTokenTypes.kTRAIT );  }
  "extends"                               {  return ( GroovyTokenTypes.kEXTENDS );  }
  "super"                                 {  return ( GroovyTokenTypes.kSUPER );  }
  "void"                                  {  return ( GroovyTokenTypes.kVOID );  }
  "boolean"                               {  return ( GroovyTokenTypes.kBOOLEAN );  }
  "byte"                                  {  return ( GroovyTokenTypes.kBYTE );  }
  "char"                                  {  return ( GroovyTokenTypes.kCHAR );  }
  "short"                                 {  return ( GroovyTokenTypes.kSHORT );  }
  "int"                                   {  return ( GroovyTokenTypes.kINT );  }
  "float"                                 {  return ( GroovyTokenTypes.kFLOAT );  }
  "long"                                  {  return ( GroovyTokenTypes.kLONG );  }
  "double"                                {  return ( GroovyTokenTypes.kDOUBLE );  }
  "as"                                    {  return ( GroovyTokenTypes.kAS );  }
  "private"                               {  return ( GroovyTokenTypes.kPRIVATE );  }
  "abstract"                              {  return ( GroovyTokenTypes.kABSTRACT );  }
  "public"                                {  return ( GroovyTokenTypes.kPUBLIC );  }
  "protected"                             {  return ( GroovyTokenTypes.kPROTECTED );  }
  "transient"                             {  return ( GroovyTokenTypes.kTRANSIENT );  }
  "native"                                {  return ( GroovyTokenTypes.kNATIVE );  }
  "synchronized"                          {  return ( GroovyTokenTypes.kSYNCHRONIZED );  }
  "volatile"                              {  return ( GroovyTokenTypes.kVOLATILE );  }
  "default"                               {  return ( GroovyTokenTypes.kDEFAULT );  }
  "do"                                    {  return ( GroovyTokenTypes.kDO );  }
  "throws"                                {  return ( GroovyTokenTypes.kTHROWS );  }
  "implements"                            {  return ( GroovyTokenTypes.kIMPLEMENTS );  }
  "this"                                  {  return ( GroovyTokenTypes.kTHIS );  }
  "if"                                    {  return ( GroovyTokenTypes.kIF );  }
  "else"                                  {  return ( GroovyTokenTypes.kELSE );  }
  "while"                                 {  return ( GroovyTokenTypes.kWHILE );  }
  "switch"                                {  return ( GroovyTokenTypes.kSWITCH );  }
  "for"                                   {  return ( GroovyTokenTypes.kFOR );  }
  "in"                                    {  return ( GroovyTokenTypes.kIN );  }
  "return"                                {  return ( GroovyTokenTypes.kRETURN );  }
  "break"                                 {  return ( GroovyTokenTypes.kBREAK );  }
  "continue"                              {  return ( GroovyTokenTypes.kCONTINUE );  }
  "throw"                                 {  return ( GroovyTokenTypes.kTHROW );  }
  "assert"                                {  return ( GroovyTokenTypes.kASSERT );  }
  "case"                                  {  return ( GroovyTokenTypes.kCASE );  }
  "try"                                   {  return ( GroovyTokenTypes.kTRY );  }
  "finally"                               {  return ( GroovyTokenTypes.kFINALLY );  }
  "catch"                                 {  return ( GroovyTokenTypes.kCATCH );  }
  "instanceof"                            {  return ( GroovyTokenTypes.kINSTANCEOF );  }
  "new"                                   {  return ( GroovyTokenTypes.kNEW );  }
  "true"                                  {  return ( GroovyTokenTypes.kTRUE );  }
  "false"                                 {  return ( GroovyTokenTypes.kFALSE );  }
  "null"                                  {  return ( GroovyTokenTypes.kNULL );  }
  "final"                                 {  return ( GroovyTokenTypes.kFINAL );  }

  {mIDENT_NOBUCKS}                        {  yybegin(IN_TRIPLE_DOT);
                                             return GroovyTokenTypes.mIDENT; }
  "{"                                     {  blockStack.push(GroovyTokenTypes.mLBRACK);
                                             braceCount.push(GroovyTokenTypes.mLCURLY);
                                             yybegin(NLS_AFTER_LBRACE);
                                             return GroovyTokenTypes.mLCURLY; }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_TRIPLE_GSTRING); }
}

<IN_TRIPLE_GSTRING> {
  {mGSTRING_TRIPLE_CONTENT} /(\"\"\")?    { return GroovyTokenTypes.mGSTRING_CONTENT; }
  {mGSTRING_TRIPLE_CONTENT}?
                     (\" (\")? | \\)      { return GroovyTokenTypes.mGSTRING_CONTENT; }

  "$"                                     {  yybegin(IN_TRIPLE_GSTRING_DOLLAR);
                                             return GroovyTokenTypes.mDOLLAR;}
  \"\"\"                                  {  if (!gStringStack.isEmpty()){
                                               gStringStack.pop();
                                             }
                                             if (blockStack.isEmpty()){
                                               yybegin(YYINITIAL);
                                             } else {
                                               yybegin(IN_INNER_BLOCK);
                                             }
                                             return GroovyTokenTypes.mGSTRING_END; }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////  regexes //////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

<WAIT_FOR_REGEX> {

  {WHITE_SPACE}                           {  afterComment = YYINITIAL;
                                           return (TokenType.WHITE_SPACE);  }

  {mSL_COMMENT}                           {  return GroovyTokenTypes.mSL_COMMENT; }
  {mML_COMMENT}                           {  return GroovyTokenTypes.mML_COMMENT; }
  {mDOC_COMMENT}                          {  return GroovyDocElementTypes.GROOVY_DOC_COMMENT; }

  "/"                                     {  yybegin(IN_REGEX);
                                             gStringStack.push(GroovyTokenTypes.mDIV);
                                             return GroovyTokenTypes.mREGEX_BEGIN; }

  "$""/"                                  {  yybegin(IN_DOLLAR_SLASH_REGEX);
                                             gStringStack.push(GroovyTokenTypes.mDOLLAR);
                                             return GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN; }

  [^]                                     {  yypushback(1);
                                             if (blockStack.isEmpty()){
                                               yybegin(YYINITIAL);
                                             } else {
                                               yybegin(IN_INNER_BLOCK);
                                             }
                                          }
}

<IN_REGEX> {
  "/"                                     {  if (!gStringStack.isEmpty()) {
                                               gStringStack.pop();
                                             }
                                             if (blockStack.isEmpty()){
                                               yybegin(YYINITIAL);
                                             } else {
                                               yybegin(IN_INNER_BLOCK);
                                             }
                                             return GroovyTokenTypes.mREGEX_END; }

  {mREGEX_CONTENT}? "$"
  /[^"{"[:letter:]"_"]                    {  return GroovyTokenTypes.mREGEX_CONTENT; }

  {mREGEX_CONTENT}                        {  return GroovyTokenTypes.mREGEX_CONTENT; }

  "$"                                     {  yybegin(IN_REGEX_DOLLAR);
                                             return GroovyTokenTypes.mDOLLAR;}
}

<IN_REGEX_DOLLAR> {

  "package"                               {  return ( GroovyTokenTypes.kPACKAGE );  }
  "strictfp"                              {  return ( GroovyTokenTypes.kSTRICTFP );  }
  "import"                                {  return ( GroovyTokenTypes.kIMPORT );  }
  "static"                                {  return ( GroovyTokenTypes.kSTATIC );  }
  "def"                                   {  return ( GroovyTokenTypes.kDEF );  }
  "class"                                 {  return ( GroovyTokenTypes.kCLASS );  }
  "interface"                             {  return ( GroovyTokenTypes.kINTERFACE );  }
  "enum"                                  {  return ( GroovyTokenTypes.kENUM );  }
  "trait"                                 {  return ( GroovyTokenTypes.kTRAIT );  }
  "extends"                               {  return ( GroovyTokenTypes.kEXTENDS );  }
  "super"                                 {  return ( GroovyTokenTypes.kSUPER );  }
  "void"                                  {  return ( GroovyTokenTypes.kVOID );  }
  "boolean"                               {  return ( GroovyTokenTypes.kBOOLEAN );  }
  "byte"                                  {  return ( GroovyTokenTypes.kBYTE );  }
  "char"                                  {  return ( GroovyTokenTypes.kCHAR );  }
  "short"                                 {  return ( GroovyTokenTypes.kSHORT );  }
  "int"                                   {  return ( GroovyTokenTypes.kINT );  }
  "float"                                 {  return ( GroovyTokenTypes.kFLOAT );  }
  "long"                                  {  return ( GroovyTokenTypes.kLONG );  }
  "double"                                {  return ( GroovyTokenTypes.kDOUBLE );  }
  "as"                                    {  return ( GroovyTokenTypes.kAS );  }
  "private"                               {  return ( GroovyTokenTypes.kPRIVATE );  }
  "abstract"                              {  return ( GroovyTokenTypes.kABSTRACT );  }
  "public"                                {  return ( GroovyTokenTypes.kPUBLIC );  }
  "protected"                             {  return ( GroovyTokenTypes.kPROTECTED );  }
  "transient"                             {  return ( GroovyTokenTypes.kTRANSIENT );  }
  "native"                                {  return ( GroovyTokenTypes.kNATIVE );  }
  "synchronized"                          {  return ( GroovyTokenTypes.kSYNCHRONIZED );  }
  "volatile"                              {  return ( GroovyTokenTypes.kVOLATILE );  }
  "default"                               {  return ( GroovyTokenTypes.kDEFAULT );  }
  "do"                                    {  return ( GroovyTokenTypes.kDO );  }
  "throws"                                {  return ( GroovyTokenTypes.kTHROWS );  }
  "implements"                            {  return ( GroovyTokenTypes.kIMPLEMENTS );  }
  "this"                                  {  return ( GroovyTokenTypes.kTHIS );  }
  "if"                                    {  return ( GroovyTokenTypes.kIF );  }
  "else"                                  {  return ( GroovyTokenTypes.kELSE );  }
  "while"                                 {  return ( GroovyTokenTypes.kWHILE );  }
  "switch"                                {  return ( GroovyTokenTypes.kSWITCH );  }
  "for"                                   {  return ( GroovyTokenTypes.kFOR );  }
  "in"                                    {  return ( GroovyTokenTypes.kIN );  }
  "return"                                {  return ( GroovyTokenTypes.kRETURN );  }
  "break"                                 {  return ( GroovyTokenTypes.kBREAK );  }
  "continue"                              {  return ( GroovyTokenTypes.kCONTINUE );  }
  "throw"                                 {  return ( GroovyTokenTypes.kTHROW );  }
  "assert"                                {  return ( GroovyTokenTypes.kASSERT );  }
  "case"                                  {  return ( GroovyTokenTypes.kCASE );  }
  "try"                                   {  return ( GroovyTokenTypes.kTRY );  }
  "finally"                               {  return ( GroovyTokenTypes.kFINALLY );  }
  "catch"                                 {  return ( GroovyTokenTypes.kCATCH );  }
  "instanceof"                            {  return ( GroovyTokenTypes.kINSTANCEOF );  }
  "new"                                   {  return ( GroovyTokenTypes.kNEW );  }
  "true"                                  {  return ( GroovyTokenTypes.kTRUE );  }
  "false"                                 {  return ( GroovyTokenTypes.kFALSE );  }
  "null"                                  {  return ( GroovyTokenTypes.kNULL );  }
  "final"                                 {  return ( GroovyTokenTypes.kFINAL );  }

  {mIDENT_NOBUCKS}                        {  yybegin(IN_REGEX_DOT);
                                             return GroovyTokenTypes.mIDENT; }
  "{"                                     {  blockStack.push(GroovyTokenTypes.mDIV);
                                             braceCount.push(GroovyTokenTypes.mLCURLY);
                                             yybegin(NLS_AFTER_LBRACE);
                                             return GroovyTokenTypes.mLCURLY; }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_REGEX); }
}

<IN_REGEX_DOT>{
  "." /{mIDENT_NOBUCKS}                   {  yybegin(IN_REGEX_IDENT);
                                             return GroovyTokenTypes.mDOT;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_REGEX);  }
}

<IN_REGEX_IDENT>{
  {mIDENT_NOBUCKS}                        {  yybegin(IN_REGEX_DOT);
                                             return GroovyTokenTypes.mIDENT;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_REGEX);  }
}

<IN_DOLLAR_SLASH_REGEX> {
  "/""$"                                  {  if (!gStringStack.isEmpty()) {
                                               gStringStack.pop();
                                             }
                                             if (blockStack.isEmpty()){
                                               yybegin(YYINITIAL);
                                             } else {
                                               yybegin(IN_INNER_BLOCK);
                                             }
                                             return GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END; }

  {mDOLLAR_SLASH_REGEX_CONTENT}? "$"
    /[^"{"[:letter:]"_"]                  {  return GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT; }

  "/"                                     {  return GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT; }

  {mDOLLAR_SLASH_REGEX_CONTENT}           {  return GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT; }

  "$"                                     {  yybegin(IN_DOLLAR_SLASH_REGEX_DOLLAR);
                                             return GroovyTokenTypes.mDOLLAR;}
}

<IN_DOLLAR_SLASH_REGEX_DOLLAR> {

  "package"                               {  return ( GroovyTokenTypes.kPACKAGE );  }
  "strictfp"                              {  return ( GroovyTokenTypes.kSTRICTFP );  }
  "import"                                {  return ( GroovyTokenTypes.kIMPORT );  }
  "static"                                {  return ( GroovyTokenTypes.kSTATIC );  }
  "def"                                   {  return ( GroovyTokenTypes.kDEF );  }
  "class"                                 {  return ( GroovyTokenTypes.kCLASS );  }
  "interface"                             {  return ( GroovyTokenTypes.kINTERFACE );  }
  "enum"                                  {  return ( GroovyTokenTypes.kENUM );  }
  "trait"                                 {  return ( GroovyTokenTypes.kTRAIT );  }
  "extends"                               {  return ( GroovyTokenTypes.kEXTENDS );  }
  "super"                                 {  return ( GroovyTokenTypes.kSUPER );  }
  "void"                                  {  return ( GroovyTokenTypes.kVOID );  }
  "boolean"                               {  return ( GroovyTokenTypes.kBOOLEAN );  }
  "byte"                                  {  return ( GroovyTokenTypes.kBYTE );  }
  "char"                                  {  return ( GroovyTokenTypes.kCHAR );  }
  "short"                                 {  return ( GroovyTokenTypes.kSHORT );  }
  "int"                                   {  return ( GroovyTokenTypes.kINT );  }
  "float"                                 {  return ( GroovyTokenTypes.kFLOAT );  }
  "long"                                  {  return ( GroovyTokenTypes.kLONG );  }
  "double"                                {  return ( GroovyTokenTypes.kDOUBLE );  }
  "as"                                    {  return ( GroovyTokenTypes.kAS );  }
  "private"                               {  return ( GroovyTokenTypes.kPRIVATE );  }
  "abstract"                              {  return ( GroovyTokenTypes.kABSTRACT );  }
  "public"                                {  return ( GroovyTokenTypes.kPUBLIC );  }
  "protected"                             {  return ( GroovyTokenTypes.kPROTECTED );  }
  "transient"                             {  return ( GroovyTokenTypes.kTRANSIENT );  }
  "native"                                {  return ( GroovyTokenTypes.kNATIVE );  }
  "synchronized"                          {  return ( GroovyTokenTypes.kSYNCHRONIZED );  }
  "volatile"                              {  return ( GroovyTokenTypes.kVOLATILE );  }
  "default"                               {  return ( GroovyTokenTypes.kDEFAULT );  }
  "do"                                    {  return ( GroovyTokenTypes.kDO );  }
  "throws"                                {  return ( GroovyTokenTypes.kTHROWS );  }
  "implements"                            {  return ( GroovyTokenTypes.kIMPLEMENTS );  }
  "this"                                  {  return ( GroovyTokenTypes.kTHIS );  }
  "if"                                    {  return ( GroovyTokenTypes.kIF );  }
  "else"                                  {  return ( GroovyTokenTypes.kELSE );  }
  "while"                                 {  return ( GroovyTokenTypes.kWHILE );  }
  "switch"                                {  return ( GroovyTokenTypes.kSWITCH );  }
  "for"                                   {  return ( GroovyTokenTypes.kFOR );  }
  "in"                                    {  return ( GroovyTokenTypes.kIN );  }
  "return"                                {  return ( GroovyTokenTypes.kRETURN );  }
  "break"                                 {  return ( GroovyTokenTypes.kBREAK );  }
  "continue"                              {  return ( GroovyTokenTypes.kCONTINUE );  }
  "throw"                                 {  return ( GroovyTokenTypes.kTHROW );  }
  "assert"                                {  return ( GroovyTokenTypes.kASSERT );  }
  "case"                                  {  return ( GroovyTokenTypes.kCASE );  }
  "try"                                   {  return ( GroovyTokenTypes.kTRY );  }
  "finally"                               {  return ( GroovyTokenTypes.kFINALLY );  }
  "catch"                                 {  return ( GroovyTokenTypes.kCATCH );  }
  "instanceof"                            {  return ( GroovyTokenTypes.kINSTANCEOF );  }
  "new"                                   {  return ( GroovyTokenTypes.kNEW );  }
  "true"                                  {  return ( GroovyTokenTypes.kTRUE );  }
  "false"                                 {  return ( GroovyTokenTypes.kFALSE );  }
  "null"                                  {  return ( GroovyTokenTypes.kNULL );  }
  "final"                                 {  return ( GroovyTokenTypes.kFINAL );  }

  {mIDENT_NOBUCKS}                        {  yybegin(IN_DOLLAR_SLASH_REGEX_DOT);
                                             return GroovyTokenTypes.mIDENT; }
  "{"                                     {  blockStack.push(GroovyTokenTypes.mDOLLAR);
                                             braceCount.push(GroovyTokenTypes.mLCURLY);
                                             yybegin(NLS_AFTER_LBRACE);
                                             return GroovyTokenTypes.mLCURLY; }

[^]                                     {  yypushback(1);
                                           yybegin(IN_DOLLAR_SLASH_REGEX); }
}

<IN_DOLLAR_SLASH_REGEX_DOT>{
  "." /{mIDENT_NOBUCKS}                   {  yybegin(IN_DOLLAR_SLASH_REGEX_IDENT);
                                             return GroovyTokenTypes.mDOT;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_DOLLAR_SLASH_REGEX);  }
}

<IN_DOLLAR_SLASH_REGEX_IDENT>{
  {mIDENT_NOBUCKS}                        {  yybegin(IN_DOLLAR_SLASH_REGEX_DOT);
                                             return GroovyTokenTypes.mIDENT;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_DOLLAR_SLASH_REGEX);  }
}



<YYINITIAL> {

"}"                                       {
                                             while (!braceCount.isEmpty() && GroovyTokenTypes.mLCURLY != braceCount.peek()) {
                                               braceCount.pop();
                                             }
                                             if (!braceCount.isEmpty() && GroovyTokenTypes.mLCURLY == braceCount.peek()) {
                                               braceCount.pop();
                                             }
                                             return GroovyTokenTypes.mRCURLY;  }

}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// White spaces & NewLines //////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{WHITE_SPACE}                             {  return TokenType.WHITE_SPACE; }
{mNLS}                                    {  yybegin(NLS_AFTER_NLS);
                                             afterComment = WAIT_FOR_REGEX;
                                             return !braceCount.isEmpty() &&
                                                 GroovyTokenTypes.mLPAREN == braceCount.peek() ? TokenType.WHITE_SPACE : GroovyTokenTypes.mNLS; }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////Comments //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mSH_COMMENT}                             {  return GroovyTokenTypes.mSH_COMMENT; }
{mSL_COMMENT}                             {  return GroovyTokenTypes.mSL_COMMENT; }
{mML_COMMENT}                             {  return GroovyTokenTypes.mML_COMMENT; }
{mDOC_COMMENT}                            {  return GroovyDocElementTypes.GROOVY_DOC_COMMENT; }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Integers and floats //////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mNUM_INT}                                {  return GroovyTokenTypes.mNUM_INT; }
{mNUM_BIG_INT}                            {  return GroovyTokenTypes.mNUM_BIG_INT; }
{mNUM_BIG_DECIMAL}                        {  return GroovyTokenTypes.mNUM_BIG_DECIMAL; }
{mNUM_FLOAT}                              {  return GroovyTokenTypes.mNUM_FLOAT; }
{mNUM_DOUBLE}                             {  return GroovyTokenTypes.mNUM_DOUBLE; }
{mNUM_LONG}                               {  return GroovyTokenTypes.mNUM_LONG; }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Strings & regular expressions ////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// Java strings
{mSTRING_LITERAL}                                          {  return GroovyTokenTypes.mSTRING_LITERAL; }
{mSINGLE_QUOTED_STRING_BEGIN}                              {  return GroovyTokenTypes.mSTRING_LITERAL; }

// GStrings
\"\"\"                                                     {  yybegin(IN_TRIPLE_GSTRING);
                                                              gStringStack.push(GroovyTokenTypes.mLBRACK);
                                                              return GroovyTokenTypes.mGSTRING_BEGIN; }

\"                                                         {  yybegin(IN_SINGLE_GSTRING);
                                                              gStringStack.push(GroovyTokenTypes.mLPAREN);
                                                              return GroovyTokenTypes.mGSTRING_BEGIN; }

{mGSTRING_LITERAL}                                         {  return GroovyTokenTypes.mGSTRING_LITERAL; }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// keywords /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

"package"                                 {  return ( GroovyTokenTypes.kPACKAGE );  }
"strictfp"                                {  return ( GroovyTokenTypes.kSTRICTFP );  }
"import"                                  {  return ( GroovyTokenTypes.kIMPORT );  }
"static"                                  {  return ( GroovyTokenTypes.kSTATIC );  }
"def"                                     {  return ( GroovyTokenTypes.kDEF );  }
"class"                                   {  return ( GroovyTokenTypes.kCLASS );  }
"interface"                               {  return ( GroovyTokenTypes.kINTERFACE );  }
"enum"                                    {  return ( GroovyTokenTypes.kENUM );  }
"trait"                                   {  return ( GroovyTokenTypes.kTRAIT );  }
"extends"                                 {  return ( GroovyTokenTypes.kEXTENDS );  }
"super"                                   {  return ( GroovyTokenTypes.kSUPER );  }
"void"                                    {  return ( GroovyTokenTypes.kVOID );  }
"boolean"                                 {  return ( GroovyTokenTypes.kBOOLEAN );  }
"byte"                                    {  return ( GroovyTokenTypes.kBYTE );  }
"char"                                    {  return ( GroovyTokenTypes.kCHAR );  }
"short"                                   {  return ( GroovyTokenTypes.kSHORT );  }
"int"                                     {  return ( GroovyTokenTypes.kINT );  }
"float"                                   {  return ( GroovyTokenTypes.kFLOAT );  }
"long"                                    {  return ( GroovyTokenTypes.kLONG );  }
"double"                                  {  return ( GroovyTokenTypes.kDOUBLE );  }
"as"                                      {  return ( GroovyTokenTypes.kAS );  }
"private"                                 {  return ( GroovyTokenTypes.kPRIVATE );  }
"abstract"                                {  return ( GroovyTokenTypes.kABSTRACT );  }
"public"                                  {  return ( GroovyTokenTypes.kPUBLIC );  }
"protected"                               {  return ( GroovyTokenTypes.kPROTECTED );  }
"transient"                               {  return ( GroovyTokenTypes.kTRANSIENT );  }
"native"                                  {  return ( GroovyTokenTypes.kNATIVE );  }
"synchronized"                            {  return ( GroovyTokenTypes.kSYNCHRONIZED );  }
"volatile"                                {  return ( GroovyTokenTypes.kVOLATILE );  }
"default"                                 {  return ( GroovyTokenTypes.kDEFAULT );  }
"do"                                      {  return ( GroovyTokenTypes.kDO );  }
"throws"                                  {  return ( GroovyTokenTypes.kTHROWS );  }
"implements"                              {  return ( GroovyTokenTypes.kIMPLEMENTS );  }
"this"                                    {  return ( GroovyTokenTypes.kTHIS );  }
"if"                                      {  return ( GroovyTokenTypes.kIF );  }
"else"                                    {  return ( GroovyTokenTypes.kELSE );  }
"while"                                   {  return ( GroovyTokenTypes.kWHILE );  }
"switch"                                  {  return ( GroovyTokenTypes.kSWITCH );  }
"for"                                     {  return ( GroovyTokenTypes.kFOR );  }
"in"                                      {  return ( GroovyTokenTypes.kIN );  }
"return"                                  {  return ( GroovyTokenTypes.kRETURN );  }
"break"                                   {  return ( GroovyTokenTypes.kBREAK );  }
"continue"                                {  return ( GroovyTokenTypes.kCONTINUE );  }
"throw"                                   {  return ( GroovyTokenTypes.kTHROW );  }
"assert"                                  {  return ( GroovyTokenTypes.kASSERT );  }
"case"                                    {  return ( GroovyTokenTypes.kCASE );  }
"try"                                     {  return ( GroovyTokenTypes.kTRY );  }
"finally"                                 {  return ( GroovyTokenTypes.kFINALLY );  }
"catch"                                   {  return ( GroovyTokenTypes.kCATCH );  }
"instanceof"                              {  return ( GroovyTokenTypes.kINSTANCEOF );  }
"new"                                     {  return ( GroovyTokenTypes.kNEW );  }
"true"                                    {  return ( GroovyTokenTypes.kTRUE );  }
"false"                                   {  return ( GroovyTokenTypes.kFALSE );  }
"null"                                    {  return ( GroovyTokenTypes.kNULL );  }
"final"                                   {  return ( GroovyTokenTypes.kFINAL );  }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      identifiers      ////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mIDENT}                                  {   return GroovyTokenTypes.mIDENT; }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Reserved shorthands //////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

"?"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(GroovyTokenTypes.mQUESTION);  }
"/"                                       {  if (zzStartRead == 0 ||
                                               zzBuffer.subSequence(0, zzStartRead).toString().trim().length() == 0) {
                                               yypushback(1);
                                               yybegin(WAIT_FOR_REGEX);
                                             } else {
                                               return(GroovyTokenTypes.mDIV);
                                             }
                                          }
"$""/"                                    {  if (zzStartRead == 0 ||
                                               zzBuffer.subSequence(0, zzStartRead).toString().trim().length() == 0) {
                                               yypushback(2);
                                               yybegin(WAIT_FOR_REGEX);
                                             } else {
                                               yypushback(1);
                                               return (GroovyTokenTypes.mDOLLAR);
                                             }
                                          }
"/="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mDIV_ASSIGN);  }
"("                                       {  yybegin(WAIT_FOR_REGEX);
                                             braceCount.push(GroovyTokenTypes.mLPAREN);
                                             return (GroovyTokenTypes.mLPAREN);  }
")"                                       {  if (!braceCount.isEmpty() && GroovyTokenTypes.mLPAREN == braceCount.peek()) {
                                               braceCount.pop();
                                             }
                                             return (GroovyTokenTypes.mRPAREN);  }
"["                                       {  yybegin(WAIT_FOR_REGEX);
                                             braceCount.push(GroovyTokenTypes.mLPAREN);
                                             return (GroovyTokenTypes.mLBRACK);  }
"]"                                       {  if (!braceCount.isEmpty() && GroovyTokenTypes.mLPAREN == braceCount.peek()) {
                                               braceCount.pop();
                                             }
                                             return (GroovyTokenTypes.mRBRACK);  }
"{"                                       {  yybegin(NLS_AFTER_LBRACE);
                                             braceCount.push(GroovyTokenTypes.mLCURLY);
                                             return (GroovyTokenTypes.mLCURLY);  }
":"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mCOLON);  }
","                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mCOMMA);  }
"."                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mDOT);  }
"="                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mASSIGN);  }
"<=>"                                     {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mCOMPARE_TO);  }
"=="|"==="                                {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mEQUAL);  }
"!"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mLNOT);  }
"~"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mBNOT);  }
"!="|"!=="                                {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mNOT_EQUAL);  }
"+"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mPLUS);  }
"+="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mPLUS_ASSIGN);  }
"++"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mINC);  }
"-"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mMINUS);  }
"-="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mMINUS_ASSIGN);  }
"--"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mDEC);  }
"*"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mSTAR);  }
"*="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mSTAR_ASSIGN);  }
"%"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mMOD);  }
"%="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mMOD_ASSIGN);  }
">>="                                     {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mSR_ASSIGN);  }
">>>="                                    {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mBSR_ASSIGN);  }
">="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mGE);  }
">"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mGT);  }
"<<="                                     {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mSL_ASSIGN);  }
"<="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mLE);  }
"?:"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mELVIS);  }
"<"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mLT);  }
"^"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mBXOR);  }
"^="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mBXOR_ASSIGN);  }
"|"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mBOR);  }
"|="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mBOR_ASSIGN);  }
"||"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mLOR);  }
"&"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mBAND);  }
"&="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mBAND_ASSIGN);  }
"&&"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mLAND);  }
";"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mSEMI);  }
"$"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mDOLLAR);  }
".."                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mRANGE_INCLUSIVE);  }
"..<"                                     {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mRANGE_EXCLUSIVE);  }
"..."                                     {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mTRIPLE_DOT);  }
"*."                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mSPREAD_DOT);  }
"?."                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mOPTIONAL_DOT);  }
".&"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mMEMBER_POINTER);  }
"=~"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mREGEX_FIND);  }
"==~"                                     {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mREGEX_MATCH);  }
"**"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mSTAR_STAR);  }
"**="                                     {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mSTAR_STAR_ASSIGN);  }
"->"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mCLOSABLE_BLOCK_OP);  }
"@"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return (GroovyTokenTypes.mAT);  }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Other ////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// Unknown symbol is using for debug goals.
.                                         {   return GroovyTokenTypes.mWRONG; }



