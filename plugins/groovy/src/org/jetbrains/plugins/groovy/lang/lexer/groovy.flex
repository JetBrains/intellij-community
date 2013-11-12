/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import java.util.*;

%%

%class _GroovyLexer
%implements FlexLexer, GroovyTokenTypes, TokenType
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

  {mSL_COMMENT}                             {  return mSL_COMMENT; }
  {mML_COMMENT}                             {  return mML_COMMENT; }
  {mDOC_COMMENT}                            {  return GROOVY_DOC_COMMENT; }

  ({mNLS}|{WHITE_SPACE})+                   {  return WHITE_SPACE; }

  [^]                                       { yypushback(1);
                                              yybegin(afterComment);  }
}
<NLS_AFTER_LBRACE>{

  ({mNLS}|{WHITE_SPACE})+                   { return WHITE_SPACE; }

  [^]                                       { yypushback(1);
                                              yybegin(WAIT_FOR_REGEX);  }
}
<NLS_AFTER_NLS>{

  ({mNLS}|{WHITE_SPACE})+                   { return WHITE_SPACE; }

  [^]                                       { yypushback(1);
                                              yybegin(NLS_AFTER_COMMENT);  }
}


// Single double-quoted GString
<IN_SINGLE_IDENT>{
  {mIDENT_NOBUCKS}                        {  yybegin(IN_SINGLE_DOT);
                                             return mIDENT;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_SINGLE_GSTRING);  }
}
<IN_SINGLE_DOT>{
  "." /{mIDENT_NOBUCKS}                   {  yybegin(IN_SINGLE_IDENT);
                                             return mDOT;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_SINGLE_GSTRING);  }
}

<IN_SINGLE_GSTRING_DOLLAR> {

  "package"                               {  return( kPACKAGE );  }
  "strictfp"                              {  return( kSTRICTFP );  }
  "import"                                {  return( kIMPORT );  }
  "static"                                {  return( kSTATIC );  }
  "def"                                   {  return( kDEF );  }
  "class"                                 {  return( kCLASS );  }
  "interface"                             {  return( kINTERFACE );  }
  "enum"                                  {  return( kENUM );  }
  "extends"                               {  return( kEXTENDS );  }
  "super"                                 {  return( kSUPER );  }
  "void"                                  {  return( kVOID );  }
  "boolean"                               {  return( kBOOLEAN );  }
  "byte"                                  {  return( kBYTE );  }
  "char"                                  {  return( kCHAR );  }
  "short"                                 {  return( kSHORT );  }
  "int"                                   {  return( kINT );  }
  "float"                                 {  return( kFLOAT );  }
  "long"                                  {  return( kLONG );  }
  "double"                                {  return( kDOUBLE );  }
  "as"                                    {  return( kAS );  }
  "private"                               {  return( kPRIVATE );  }
  "abstract"                              {  return( kABSTRACT );  }
  "public"                                {  return( kPUBLIC );  }
  "protected"                             {  return( kPROTECTED );  }
  "transient"                             {  return( kTRANSIENT );  }
  "native"                                {  return( kNATIVE );  }
  "synchronized"                          {  return( kSYNCHRONIZED );  }
  "volatile"                              {  return( kVOLATILE );  }
  "default"                               {  return( kDEFAULT );  }
  "do"                                    {  return( kDO );  }
  "throws"                                {  return( kTHROWS );  }
  "implements"                            {  return( kIMPLEMENTS );  }
  "this"                                  {  return( kTHIS );  }
  "if"                                    {  return( kIF );  }
  "else"                                  {  return( kELSE );  }
  "while"                                 {  return( kWHILE );  }
  "switch"                                {  return( kSWITCH );  }
  "for"                                   {  return( kFOR );  }
  "in"                                    {  return( kIN );  }
  "return"                                {  return( kRETURN );  }
  "break"                                 {  return( kBREAK );  }
  "continue"                              {  return( kCONTINUE );  }
  "throw"                                 {  return( kTHROW );  }
  "assert"                                {  return( kASSERT );  }
  "case"                                  {  return( kCASE );  }
  "try"                                   {  return( kTRY );  }
  "finally"                               {  return( kFINALLY );  }
  "catch"                                 {  return( kCATCH );  }
  "instanceof"                            {  return( kINSTANCEOF );  }
  "new"                                   {  return( kNEW );  }
  "true"                                  {  return( kTRUE );  }
  "false"                                 {  return( kFALSE );  }
  "null"                                  {  return( kNULL );  }
  "final"                                 {  return( kFINAL );  }

  {mIDENT_NOBUCKS}                        {  yybegin(IN_SINGLE_DOT);
                                             return mIDENT; }
  "{"                                     {  blockStack.push(mLPAREN);
                                             braceCount.push(mLCURLY);
                                             yybegin(NLS_AFTER_LBRACE);
                                             return mLCURLY; }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_SINGLE_GSTRING); }
}

<IN_SINGLE_GSTRING> {
  {mGSTRING_SINGLE_CONTENT} (\\)?         {  return mGSTRING_CONTENT; }
  \\                                      {  return mGSTRING_CONTENT; }

  \"                                      {  if (!gStringStack.isEmpty()) {
                                               gStringStack.pop();
                                             }
                                             if (blockStack.isEmpty()){
                                               yybegin(YYINITIAL);
                                             } else {
                                               yybegin(IN_INNER_BLOCK);
                                             }
                                             return mGSTRING_END; }
  "$"                                     {  yybegin(IN_SINGLE_GSTRING_DOLLAR);
                                             return mDOLLAR;
                                          }
  {mNLS}                                  {  clearStacks();
                                             yybegin(NLS_AFTER_NLS);
                                             afterComment = YYINITIAL;
                                             return mNLS; }
}

<IN_INNER_BLOCK>{
  "{"                                     {  blockStack.push(mLCURLY);
                                             braceCount.push(mLCURLY);
                                             yybegin(NLS_AFTER_LBRACE);
                                             return(mLCURLY);  }

  "}"                                     {  if (!blockStack.isEmpty()) {
                                               IElementType br = blockStack.pop();
                                               if (br.equals(mLPAREN)) yybegin(IN_SINGLE_GSTRING);
                                               if (br.equals(mLBRACK)) yybegin(IN_TRIPLE_GSTRING);
                                               if (br.equals(mDIV)) yybegin(IN_REGEX);
                                               if (br.equals(mDOLLAR)) yybegin(IN_DOLLAR_SLASH_REGEX);
                                             }
                                             while (!braceCount.isEmpty() && mLCURLY != braceCount.peek()) {
                                               braceCount.pop();
                                             }
                                             if (!braceCount.isEmpty() && mLCURLY == braceCount.peek()) {
                                               braceCount.pop();
                                             }
                                             return mRCURLY; }
}

// Triple double-quoted GString
<IN_TRIPLE_IDENT>{
  {mIDENT_NOBUCKS}                        {  yybegin(IN_TRIPLE_DOT);
                                             return mIDENT;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_TRIPLE_GSTRING);  }
}
<IN_TRIPLE_DOT>{
  "." /{mIDENT_NOBUCKS}                   {  yybegin(IN_TRIPLE_NLS);
                                             return mDOT;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_TRIPLE_GSTRING);  }
}
<IN_TRIPLE_NLS>{
  {mNLS}                                  {  yybegin(NLS_AFTER_NLS);
                                             afterComment = IN_TRIPLE_IDENT;
                                             return mNLS;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_TRIPLE_IDENT);  }

}

<IN_TRIPLE_GSTRING_DOLLAR> {
  "package"                               {  return( kPACKAGE );  }
  "strictfp"                              {  return( kSTRICTFP );  }
  "import"                                {  return( kIMPORT );  }
  "static"                                {  return( kSTATIC );  }
  "def"                                   {  return( kDEF );  }
  "class"                                 {  return( kCLASS );  }
  "interface"                             {  return( kINTERFACE );  }
  "enum"                                  {  return( kENUM );  }
  "extends"                               {  return( kEXTENDS );  }
  "super"                                 {  return( kSUPER );  }
  "void"                                  {  return( kVOID );  }
  "boolean"                               {  return( kBOOLEAN );  }
  "byte"                                  {  return( kBYTE );  }
  "char"                                  {  return( kCHAR );  }
  "short"                                 {  return( kSHORT );  }
  "int"                                   {  return( kINT );  }
  "float"                                 {  return( kFLOAT );  }
  "long"                                  {  return( kLONG );  }
  "double"                                {  return( kDOUBLE );  }
  "as"                                    {  return( kAS );  }
  "private"                               {  return( kPRIVATE );  }
  "abstract"                              {  return( kABSTRACT );  }
  "public"                                {  return( kPUBLIC );  }
  "protected"                             {  return( kPROTECTED );  }
  "transient"                             {  return( kTRANSIENT );  }
  "native"                                {  return( kNATIVE );  }
  "synchronized"                          {  return( kSYNCHRONIZED );  }
  "volatile"                              {  return( kVOLATILE );  }
  "default"                               {  return( kDEFAULT );  }
  "do"                                    {  return( kDO );  }
  "throws"                                {  return( kTHROWS );  }
  "implements"                            {  return( kIMPLEMENTS );  }
  "this"                                  {  return( kTHIS );  }
  "if"                                    {  return( kIF );  }
  "else"                                  {  return( kELSE );  }
  "while"                                 {  return( kWHILE );  }
  "switch"                                {  return( kSWITCH );  }
  "for"                                   {  return( kFOR );  }
  "in"                                    {  return( kIN );  }
  "return"                                {  return( kRETURN );  }
  "break"                                 {  return( kBREAK );  }
  "continue"                              {  return( kCONTINUE );  }
  "throw"                                 {  return( kTHROW );  }
  "assert"                                {  return( kASSERT );  }
  "case"                                  {  return( kCASE );  }
  "try"                                   {  return( kTRY );  }
  "finally"                               {  return( kFINALLY );  }
  "catch"                                 {  return( kCATCH );  }
  "instanceof"                            {  return( kINSTANCEOF );  }
  "new"                                   {  return( kNEW );  }
  "true"                                  {  return( kTRUE );  }
  "false"                                 {  return( kFALSE );  }
  "null"                                  {  return( kNULL );  }
  "final"                                 {  return( kFINAL );  }

  {mIDENT_NOBUCKS}                        {  yybegin(IN_TRIPLE_DOT);
                                             return mIDENT; }
  "{"                                     {  blockStack.push(mLBRACK);
                                             braceCount.push(mLCURLY);
                                             yybegin(NLS_AFTER_LBRACE);
                                             return mLCURLY; }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_TRIPLE_GSTRING); }
}

<IN_TRIPLE_GSTRING> {
  {mGSTRING_TRIPLE_CONTENT} /(\"\"\")?    { return mGSTRING_CONTENT; }
  {mGSTRING_TRIPLE_CONTENT}?
                     (\" (\")? | \\)      { return mGSTRING_CONTENT; }

  "$"                                     {  yybegin(IN_TRIPLE_GSTRING_DOLLAR);
                                             return mDOLLAR;}
  \"\"\"                                  {  if (!gStringStack.isEmpty()){
                                               gStringStack.pop();
                                             }
                                             if (blockStack.isEmpty()){
                                               yybegin(YYINITIAL);
                                             } else {
                                               yybegin(IN_INNER_BLOCK);
                                             }
                                             return mGSTRING_END; }
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////  regexes //////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

<WAIT_FOR_REGEX> {

  {WHITE_SPACE}                           {  afterComment = YYINITIAL;
                                           return(WHITE_SPACE);  }

  {mSL_COMMENT}                           {  return mSL_COMMENT; }
  {mML_COMMENT}                           {  return mML_COMMENT; }
  {mDOC_COMMENT}                          {  return GROOVY_DOC_COMMENT; }

  "/"                                     {  yybegin(IN_REGEX);
                                             gStringStack.push(mDIV);
                                             return mREGEX_BEGIN; }

  "$""/"                                  {  yybegin(IN_DOLLAR_SLASH_REGEX);
                                             gStringStack.push(mDOLLAR);
                                             return mDOLLAR_SLASH_REGEX_BEGIN; }

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
                                             return mREGEX_END; }

  {mREGEX_CONTENT}? "$"
  /[^"{"[:letter:]"_"]                    {  return mREGEX_CONTENT; }

  {mREGEX_CONTENT}                        {  return mREGEX_CONTENT; }

  "$"                                     {  yybegin(IN_REGEX_DOLLAR);
                                             return mDOLLAR;}
}

<IN_REGEX_DOLLAR> {

  "package"                               {  return( kPACKAGE );  }
  "strictfp"                              {  return( kSTRICTFP );  }
  "import"                                {  return( kIMPORT );  }
  "static"                                {  return( kSTATIC );  }
  "def"                                   {  return( kDEF );  }
  "class"                                 {  return( kCLASS );  }
  "interface"                             {  return( kINTERFACE );  }
  "enum"                                  {  return( kENUM );  }
  "extends"                               {  return( kEXTENDS );  }
  "super"                                 {  return( kSUPER );  }
  "void"                                  {  return( kVOID );  }
  "boolean"                               {  return( kBOOLEAN );  }
  "byte"                                  {  return( kBYTE );  }
  "char"                                  {  return( kCHAR );  }
  "short"                                 {  return( kSHORT );  }
  "int"                                   {  return( kINT );  }
  "float"                                 {  return( kFLOAT );  }
  "long"                                  {  return( kLONG );  }
  "double"                                {  return( kDOUBLE );  }
  "as"                                    {  return( kAS );  }
  "private"                               {  return( kPRIVATE );  }
  "abstract"                              {  return( kABSTRACT );  }
  "public"                                {  return( kPUBLIC );  }
  "protected"                             {  return( kPROTECTED );  }
  "transient"                             {  return( kTRANSIENT );  }
  "native"                                {  return( kNATIVE );  }
  "synchronized"                          {  return( kSYNCHRONIZED );  }
  "volatile"                              {  return( kVOLATILE );  }
  "default"                               {  return( kDEFAULT );  }
  "do"                                    {  return( kDO );  }
  "throws"                                {  return( kTHROWS );  }
  "implements"                            {  return( kIMPLEMENTS );  }
  "this"                                  {  return( kTHIS );  }
  "if"                                    {  return( kIF );  }
  "else"                                  {  return( kELSE );  }
  "while"                                 {  return( kWHILE );  }
  "switch"                                {  return( kSWITCH );  }
  "for"                                   {  return( kFOR );  }
  "in"                                    {  return( kIN );  }
  "return"                                {  return( kRETURN );  }
  "break"                                 {  return( kBREAK );  }
  "continue"                              {  return( kCONTINUE );  }
  "throw"                                 {  return( kTHROW );  }
  "assert"                                {  return( kASSERT );  }
  "case"                                  {  return( kCASE );  }
  "try"                                   {  return( kTRY );  }
  "finally"                               {  return( kFINALLY );  }
  "catch"                                 {  return( kCATCH );  }
  "instanceof"                            {  return( kINSTANCEOF );  }
  "new"                                   {  return( kNEW );  }
  "true"                                  {  return( kTRUE );  }
  "false"                                 {  return( kFALSE );  }
  "null"                                  {  return( kNULL );  }
  "final"                                 {  return( kFINAL );  }

  {mIDENT_NOBUCKS}                        {  yybegin(IN_REGEX_DOT);
                                             return mIDENT; }
  "{"                                     {  blockStack.push(mDIV);
                                             braceCount.push(mLCURLY);
                                             yybegin(NLS_AFTER_LBRACE);
                                             return mLCURLY; }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_REGEX); }
}

<IN_REGEX_DOT>{
  "." /{mIDENT_NOBUCKS}                   {  yybegin(IN_REGEX_IDENT);
                                             return mDOT;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_REGEX);  }
}

<IN_REGEX_IDENT>{
  {mIDENT_NOBUCKS}                        {  yybegin(IN_REGEX_DOT);
                                             return mIDENT;  }
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
                                             return mDOLLAR_SLASH_REGEX_END; }

  {mDOLLAR_SLASH_REGEX_CONTENT}? "$"
    /[^"{"[:letter:]"_"]                  {  return mDOLLAR_SLASH_REGEX_CONTENT; }

  "/"                                     {  return mDOLLAR_SLASH_REGEX_CONTENT; }

  {mDOLLAR_SLASH_REGEX_CONTENT}           {  return mDOLLAR_SLASH_REGEX_CONTENT; }

  "$"                                     {  yybegin(IN_DOLLAR_SLASH_REGEX_DOLLAR);
                                             return mDOLLAR;}
}

<IN_DOLLAR_SLASH_REGEX_DOLLAR> {

  "package"                               {  return( kPACKAGE );  }
  "strictfp"                              {  return( kSTRICTFP );  }
  "import"                                {  return( kIMPORT );  }
  "static"                                {  return( kSTATIC );  }
  "def"                                   {  return( kDEF );  }
  "class"                                 {  return( kCLASS );  }
  "interface"                             {  return( kINTERFACE );  }
  "enum"                                  {  return( kENUM );  }
  "extends"                               {  return( kEXTENDS );  }
  "super"                                 {  return( kSUPER );  }
  "void"                                  {  return( kVOID );  }
  "boolean"                               {  return( kBOOLEAN );  }
  "byte"                                  {  return( kBYTE );  }
  "char"                                  {  return( kCHAR );  }
  "short"                                 {  return( kSHORT );  }
  "int"                                   {  return( kINT );  }
  "float"                                 {  return( kFLOAT );  }
  "long"                                  {  return( kLONG );  }
  "double"                                {  return( kDOUBLE );  }
  "as"                                    {  return( kAS );  }
  "private"                               {  return( kPRIVATE );  }
  "abstract"                              {  return( kABSTRACT );  }
  "public"                                {  return( kPUBLIC );  }
  "protected"                             {  return( kPROTECTED );  }
  "transient"                             {  return( kTRANSIENT );  }
  "native"                                {  return( kNATIVE );  }
  "synchronized"                          {  return( kSYNCHRONIZED );  }
  "volatile"                              {  return( kVOLATILE );  }
  "default"                               {  return( kDEFAULT );  }
  "do"                                    {  return( kDO );  }
  "throws"                                {  return( kTHROWS );  }
  "implements"                            {  return( kIMPLEMENTS );  }
  "this"                                  {  return( kTHIS );  }
  "if"                                    {  return( kIF );  }
  "else"                                  {  return( kELSE );  }
  "while"                                 {  return( kWHILE );  }
  "switch"                                {  return( kSWITCH );  }
  "for"                                   {  return( kFOR );  }
  "in"                                    {  return( kIN );  }
  "return"                                {  return( kRETURN );  }
  "break"                                 {  return( kBREAK );  }
  "continue"                              {  return( kCONTINUE );  }
  "throw"                                 {  return( kTHROW );  }
  "assert"                                {  return( kASSERT );  }
  "case"                                  {  return( kCASE );  }
  "try"                                   {  return( kTRY );  }
  "finally"                               {  return( kFINALLY );  }
  "catch"                                 {  return( kCATCH );  }
  "instanceof"                            {  return( kINSTANCEOF );  }
  "new"                                   {  return( kNEW );  }
  "true"                                  {  return( kTRUE );  }
  "false"                                 {  return( kFALSE );  }
  "null"                                  {  return( kNULL );  }
  "final"                                 {  return( kFINAL );  }

  {mIDENT_NOBUCKS}                        {  yybegin(IN_DOLLAR_SLASH_REGEX_DOT);
                                             return mIDENT; }
  "{"                                     {  blockStack.push(mDOLLAR);
                                             braceCount.push(mLCURLY);
                                             yybegin(NLS_AFTER_LBRACE);
                                             return mLCURLY; }

[^]                                     {  yypushback(1);
                                           yybegin(IN_DOLLAR_SLASH_REGEX); }
}

<IN_DOLLAR_SLASH_REGEX_DOT>{
  "." /{mIDENT_NOBUCKS}                   {  yybegin(IN_DOLLAR_SLASH_REGEX_IDENT);
                                             return mDOT;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_DOLLAR_SLASH_REGEX);  }
}

<IN_DOLLAR_SLASH_REGEX_IDENT>{
  {mIDENT_NOBUCKS}                        {  yybegin(IN_DOLLAR_SLASH_REGEX_DOT);
                                             return mIDENT;  }
  [^]                                     {  yypushback(1);
                                             yybegin(IN_DOLLAR_SLASH_REGEX);  }
}



<YYINITIAL> {

"}"                                       {
                                             while (!braceCount.isEmpty() && mLCURLY != braceCount.peek()) {
                                               braceCount.pop();
                                             }
                                             if (!braceCount.isEmpty() && mLCURLY == braceCount.peek()) {
                                               braceCount.pop();
                                             }
                                             return mRCURLY;  }

}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// White spaces & NewLines //////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{WHITE_SPACE}                             {  return WHITE_SPACE; }
{mNLS}                                    {  yybegin(NLS_AFTER_NLS);
                                             afterComment = WAIT_FOR_REGEX;
                                             return !braceCount.isEmpty() &&
                                                 mLPAREN == braceCount.peek() ? WHITE_SPACE : mNLS; }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////Comments //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mSH_COMMENT}                             {  return mSH_COMMENT; }
{mSL_COMMENT}                             {  return mSL_COMMENT; }
{mML_COMMENT}                             {  return mML_COMMENT; }
{mDOC_COMMENT}                            {  return GROOVY_DOC_COMMENT; }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Integers and floats //////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mNUM_INT}                                {  return mNUM_INT; }
{mNUM_BIG_INT}                            {  return mNUM_BIG_INT; }
{mNUM_BIG_DECIMAL}                        {  return mNUM_BIG_DECIMAL; }
{mNUM_FLOAT}                              {  return mNUM_FLOAT; }
{mNUM_DOUBLE}                             {  return mNUM_DOUBLE; }
{mNUM_LONG}                               {  return mNUM_LONG; }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Strings & regular expressions ////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// Java strings
{mSTRING_LITERAL}                                          {  return mSTRING_LITERAL; }
{mSINGLE_QUOTED_STRING_BEGIN}                              {  return mSTRING_LITERAL; }

// GStrings
\"\"\"                                                     {  yybegin(IN_TRIPLE_GSTRING);
                                                              gStringStack.push(mLBRACK);
                                                              return mGSTRING_BEGIN; }

\"                                                         {  yybegin(IN_SINGLE_GSTRING);
                                                              gStringStack.push(mLPAREN);
                                                              return mGSTRING_BEGIN; }

{mGSTRING_LITERAL}                                         {  return mGSTRING_LITERAL; }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// keywords /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

"package"                                 {  return( kPACKAGE );  }
"strictfp"                                {  return( kSTRICTFP );  }
"import"                                  {  return( kIMPORT );  }
"static"                                  {  return( kSTATIC );  }
"def"                                     {  return( kDEF );  }
"class"                                   {  return( kCLASS );  }
"interface"                               {  return( kINTERFACE );  }
"enum"                                    {  return( kENUM );  }
"extends"                                 {  return( kEXTENDS );  }
"super"                                   {  return( kSUPER );  }
"void"                                    {  return( kVOID );  }
"boolean"                                 {  return( kBOOLEAN );  }
"byte"                                    {  return( kBYTE );  }
"char"                                    {  return( kCHAR );  }
"short"                                   {  return( kSHORT );  }
"int"                                     {  return( kINT );  }
"float"                                   {  return( kFLOAT );  }
"long"                                    {  return( kLONG );  }
"double"                                  {  return( kDOUBLE );  }
"as"                                      {  return( kAS );  }
"private"                                 {  return( kPRIVATE );  }
"abstract"                                {  return( kABSTRACT );  }
"public"                                  {  return( kPUBLIC );  }
"protected"                               {  return( kPROTECTED );  }
"transient"                               {  return( kTRANSIENT );  }
"native"                                  {  return( kNATIVE );  }
"synchronized"                            {  return( kSYNCHRONIZED );  }
"volatile"                                {  return( kVOLATILE );  }
"default"                                 {  return( kDEFAULT );  }
"do"                                      {  return( kDO );  }
"throws"                                  {  return( kTHROWS );  }
"implements"                              {  return( kIMPLEMENTS );  }
"this"                                    {  return( kTHIS );  }
"if"                                      {  return( kIF );  }
"else"                                    {  return( kELSE );  }
"while"                                   {  return( kWHILE );  }
"switch"                                  {  return( kSWITCH );  }
"for"                                     {  return( kFOR );  }
"in"                                      {  return( kIN );  }
"return"                                  {  return( kRETURN );  }
"break"                                   {  return( kBREAK );  }
"continue"                                {  return( kCONTINUE );  }
"throw"                                   {  return( kTHROW );  }
"assert"                                  {  return( kASSERT );  }
"case"                                    {  return( kCASE );  }
"try"                                     {  return( kTRY );  }
"finally"                                 {  return( kFINALLY );  }
"catch"                                   {  return( kCATCH );  }
"instanceof"                              {  return( kINSTANCEOF );  }
"new"                                     {  return( kNEW );  }
"true"                                    {  return( kTRUE );  }
"false"                                   {  return( kFALSE );  }
"null"                                    {  return( kNULL );  }
"final"                                   {  return( kFINAL );  }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      identifiers      ////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mIDENT}                                  {   return mIDENT; }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Reserved shorthands //////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

"?"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mQUESTION);  }
"/"                                       {  if (zzStartRead == 0 ||
                                               zzBuffer.subSequence(0, zzStartRead).toString().trim().length() == 0) {
                                               yypushback(1);
                                               yybegin(WAIT_FOR_REGEX);
                                             } else {
                                               return(mDIV);
                                             }
                                          }
"$""/"                                    {  if (zzStartRead == 0 ||
                                               zzBuffer.subSequence(0, zzStartRead).toString().trim().length() == 0) {
                                               yypushback(2);
                                               yybegin(WAIT_FOR_REGEX);
                                             } else {
                                               yypushback(1);
                                               return(mDOLLAR);
                                             }
                                          }
"/="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mDIV_ASSIGN);  }
"("                                       {  yybegin(WAIT_FOR_REGEX);
                                             braceCount.push(mLPAREN);
                                             return(mLPAREN);  }
")"                                       {  if (!braceCount.isEmpty() && mLPAREN == braceCount.peek()) {
                                               braceCount.pop();
                                             }
                                             return(mRPAREN);  }
"["                                       {  yybegin(WAIT_FOR_REGEX);
                                             braceCount.push(mLPAREN);
                                             return(mLBRACK);  }
"]"                                       {  if (!braceCount.isEmpty() && mLPAREN == braceCount.peek()) {
                                               braceCount.pop();
                                             }
                                             return(mRBRACK);  }
"{"                                       {  yybegin(NLS_AFTER_LBRACE);
                                             braceCount.push(mLCURLY);
                                             return(mLCURLY);  }
":"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mCOLON);  }
","                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mCOMMA);  }
"."                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mDOT);  }
"="                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mASSIGN);  }
"<=>"                                     {  yybegin(WAIT_FOR_REGEX);
                                             return(mCOMPARE_TO);  }
"=="|"==="                                {  yybegin(WAIT_FOR_REGEX);
                                             return(mEQUAL);  }
"!"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mLNOT);  }
"~"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mBNOT);  }
"!="|"!=="                                {  yybegin(WAIT_FOR_REGEX);
                                             return(mNOT_EQUAL);  }
"+"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mPLUS);  }
"+="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mPLUS_ASSIGN);  }
"++"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mINC);  }
"-"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mMINUS);  }
"-="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mMINUS_ASSIGN);  }
"--"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mDEC);  }
"*"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mSTAR);  }
"*="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mSTAR_ASSIGN);  }
"%"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mMOD);  }
"%="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mMOD_ASSIGN);  }
">>="                                     {  yybegin(WAIT_FOR_REGEX);
                                             return(mSR_ASSIGN);  }
">>>="                                    {  yybegin(WAIT_FOR_REGEX);
                                             return(mBSR_ASSIGN);  }
">="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mGE);  }
">"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mGT);  }
"<<="                                     {  yybegin(WAIT_FOR_REGEX);
                                             return(mSL_ASSIGN);  }
"<="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mLE);  }
"?:"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mELVIS);  }
"<"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mLT);  }
"^"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mBXOR);  }
"^="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mBXOR_ASSIGN);  }
"|"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mBOR);  }
"|="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mBOR_ASSIGN);  }
"||"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mLOR);  }
"&"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mBAND);  }
"&="                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mBAND_ASSIGN);  }
"&&"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mLAND);  }
";"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mSEMI);  }
"$"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mDOLLAR);  }
".."                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mRANGE_INCLUSIVE);  }
"..<"                                     {  yybegin(WAIT_FOR_REGEX);
                                             return(mRANGE_EXCLUSIVE);  }
"..."                                     {  yybegin(WAIT_FOR_REGEX);
                                             return(mTRIPLE_DOT);  }
"*."                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mSPREAD_DOT);  }
"?."                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mOPTIONAL_DOT);  }
".&"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mMEMBER_POINTER);  }
"=~"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mREGEX_FIND);  }
"==~"                                     {  yybegin(WAIT_FOR_REGEX);
                                             return(mREGEX_MATCH);  }
"**"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mSTAR_STAR);  }
"**="                                     {  yybegin(WAIT_FOR_REGEX);
                                             return(mSTAR_STAR_ASSIGN);  }
"->"                                      {  yybegin(WAIT_FOR_REGEX);
                                             return(mCLOSABLE_BLOCK_OP);  }
"@"                                       {  yybegin(WAIT_FOR_REGEX);
                                             return(mAT);  }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Other ////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// Unknown symbol is using for debug goals.
.                                         {   return mWRONG; }



