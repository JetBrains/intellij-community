package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import java.util.*;
import java.lang.reflect.Field;
import org.jetbrains.annotations.NotNull;

%%

%class _GroovyLexer
%implements FlexLexer, GroovyTokenTypes
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

  private void clearStacks(){
    gStringStack.clear();
    blockStack.clear();
  }

%}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// NewLines and spaces /////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mONE_NL = \r | \n | \r\n                                    // NewLines
mWS = " " | \t | \f | \\ {mONE_NL}                          // Whitespaces
mNLS = {mONE_NL}+

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Comments ////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

C_STYLE_COMMENT=("/*" [^"*"] {COMMENT_TAIL} ) | "/*"
DOC_COMMENT="/*" "*"+ ( "/" | ( [^"/""*"] {COMMENT_TAIL} ) )?
COMMENT_TAIL=( [^"*"]* ("*"+ [^"*""/"] )? )* ("*"+"/")?

mSH_COMMENT = "#!"[^\r\n]*
mSL_COMMENT = "/""/"[^\r\n]*
mML_COMMENT = {C_STYLE_COMMENT} | {DOC_COMMENT}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      integers and floats     /////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mHEX_DIGIT = [0-9A-Fa-f]
mDIGIT = [0-9]
mBIG_SUFFIX = g | G
mFLOAT_SUFFIX = f | F | d | D
mEXPONENT = (e | E)("+" | "-")?([0-9])+

mNUM_INT = ( 0
 ( (x | X)({mHEX_DIGIT})+
   | {mDIGIT}+
   | ([0-7])+
 )?
 | {mDIGIT}+
) ( (l | L)
 | (i | I)
 | {mBIG_SUFFIX}
 | ("." {mDIGIT}+ {mEXPONENT}? ({mFLOAT_SUFFIX}|{mBIG_SUFFIX})? )
 | {mEXPONENT} ({mFLOAT_SUFFIX}|{mBIG_SUFFIX})?
 | {mFLOAT_SUFFIX}
)?


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      identifiers      ////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mLETTER = !(!([:jletter:] | "_") | "$")

mIDENT = {mLETTER} ({mLETTER} | {mDIGIT})*


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// String & regexprs ///////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mSTRING_NL = {mONE_NL}
mSTRING_ESC = \\ n | \\ r | \\ t | \\ b | \\ f | \\ \" | \\ \' | \\ \\ | \\ "$"
| "\\""u"{mHEX_DIGIT}{4}
| "\\" [0..3] ([0..7] ([0..7])?)?
| "\\" [4..7] ([0..7])?
| "\\" {mONE_NL}

mSINGLE_QUOTED_STRING_BEGIN = "\'" ( {mSTRING_ESC}
    | "\""
    | [^\'\r\n]
    | "$" )*
mSINGLE_QUOTED_STRING = {mSINGLE_QUOTED_STRING_BEGIN} \'
mTRIPLE_QUOTED_STRING = "\'\'\'" ({mSTRING_ESC}
    | "\""
    | "$"
    | [^\']
    | {mSTRING_NL}
    | \'(\')?[^\'] )* (\'\'\' | \\)?

mSTRING_LITERAL = {mTRIPLE_QUOTED_STRING}
    | {mSINGLE_QUOTED_STRING}


// Single-double-quoted GStrings
mGSTRING_SINGLE_BEGIN = \""$" 
    |  \" ([^\""$"] | {mSTRING_ESC})? {mGSTRING_SINGLE_CONTENT}"$"
mGSTRING_SINGLE_CONTENT = ({mSTRING_ESC}
    | [^\"\r\n"$"]
    | "\'" )*
mGSTRING_SINGLE_CTOR_END = {mGSTRING_SINGLE_CONTENT}  \"

// Triple-double-quoted GStrings
mGSTRING_TRIPLE_BEGIN = \"\"\""$"
    |  \"\"\" ([^\""$"] | {mSTRING_ESC})? {mGSTRING_TRIPLE_CONTENT}"$"
mGSTRING_TRIPLE_CONTENT = ({mSTRING_ESC}
    | \'
    | \" (\")? [^\"]
    | [^\""$"]
    | {mSTRING_NL} )*
mGSTRING_TRIPLE_CTOR_END = {mGSTRING_TRIPLE_CONTENT}  \"\"\"

mGSTRING_TRIPLE_CTOR_END = ( {mSTRING_ESC}
    | \'
    | \" (\")? [^\"]
    | [^\""$"]
    | {mSTRING_NL} )* (\"\"\" | \\)?


mGSTRING_LITERAL = \"\"
    | \" ([^\"\n\r"$"] | {mSTRING_ESC})? {mGSTRING_SINGLE_CONTENT} \"
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
%xstate GSTRING_STAR_SINGLE
%xstate GSTRING_STAR_TRIPLE
%xstate WRONG_STRING
%state IN_INNER_BLOCK

%%
// Star meeting in Gstring
<GSTRING_STAR_SINGLE> {
  "*"                                     { return mSTAR; }
  [^"*"]                                  { yypushback(yytext().length());
                                            yybegin(IN_SINGLE_GSTRING_DOLLAR);  }
}
<GSTRING_STAR_TRIPLE> {
  "*"                                     { return mSTAR; }
  [^"*"]                                  { yypushback(yytext().length());
                                            yybegin(IN_TRIPLE_GSTRING_DOLLAR);  }
}

// Single double-quoted GString
<IN_SINGLE_IDENT>{
  {mIDENT}                                {  yybegin(IN_SINGLE_DOT);
                                             return mIDENT;  }
  [^]                                     {  yypushback(yytext().length());
                                             yybegin(IN_SINGLE_GSTRING);  }
}
<IN_SINGLE_DOT>{
  "."                                     {  yybegin(IN_SINGLE_IDENT);
                                             return mDOT;  }
  [^"."]                                  {  yypushback(yytext().length());
                                             yybegin(IN_SINGLE_GSTRING);  }
}

<IN_SINGLE_GSTRING_DOLLAR> {

  {mIDENT}                                {  yybegin(IN_SINGLE_DOT);
                                             return mIDENT; }
  "{"                                     {  blockStack.push(mLPAREN);
                                             yybegin(IN_INNER_BLOCK);
                                             return mLCURLY; }
  [^{[:jletter:]\n\r] [^\n\r]*            {  gStringStack.clear();
                                             yybegin(YYINITIAL);
                                             return mWRONG_GSTRING_LITERAL;  }
  {mNLS}                                  {  yybegin(YYINITIAL);
                                             clearStacks();
                                             return mNLS;}
}

<IN_SINGLE_GSTRING> {
  {mGSTRING_SINGLE_CONTENT}"$"            {  yybegin(GSTRING_STAR_SINGLE);
                                             return mGSTRING_SINGLE_CONTENT; }
  {mGSTRING_SINGLE_CONTENT}"\""           {  gStringStack.pop();
                                             if (blockStack.isEmpty()){
                                               yybegin(YYINITIAL);
                                             } else {
                                               yybegin(IN_INNER_BLOCK);
                                             }
                                             return mGSTRING_SINGLE_END; }
  {mGSTRING_SINGLE_CONTENT}               {  gStringStack.clear();
                                             yybegin(YYINITIAL);
                                             return mWRONG_GSTRING_LITERAL; }
  {mNLS}                                  {  clearStacks();
                                             yybegin(YYINITIAL);
                                             return mNLS; }
}

<WRONG_STRING>{
  [^]*                                    {  yybegin(YYINITIAL);
                                             return mWRONG_GSTRING_LITERAL;  }
}

<IN_INNER_BLOCK>{
  "{"                                     {  blockStack.push(mLCURLY);
                                             return(mLCURLY);  }

  "}"                                     {  if (!blockStack.isEmpty()) {
                                               IElementType br = blockStack.pop();
                                               if (br.equals(mLPAREN)) yybegin(IN_SINGLE_GSTRING);
                                               if (br.equals(mLBRACK)) yybegin(IN_TRIPLE_GSTRING);
                                             }
                                             return mRCURLY; }
}

// Triple double-quoted GString
<IN_TRIPLE_IDENT>{
  {mIDENT}                                {  yybegin(IN_TRIPLE_DOT);
                                             return mIDENT;  }
  [^]                                     {  yypushback(yytext().length());
                                             yybegin(IN_TRIPLE_GSTRING);  }
}
<IN_TRIPLE_DOT>{
  "."                                     {  yybegin(IN_TRIPLE_NLS);
                                             return mDOT;  }
  [^"."]                                  {  yypushback(yytext().length());
                                             yybegin(IN_TRIPLE_GSTRING);  }
}
<IN_TRIPLE_NLS>{
  {mNLS}{mWS}*                            {  yybegin(IN_TRIPLE_IDENT);
                                             return mNLS;  }
  [^]                                     {  yypushback(yytext().length());
                                             yybegin(IN_TRIPLE_IDENT);  }

}

<IN_TRIPLE_GSTRING_DOLLAR> {
  {mIDENT}                                {  yybegin(IN_TRIPLE_DOT);
                                             return mIDENT; }
  "{"                                     {  blockStack.push(mLBRACK);
                                             yybegin(IN_INNER_BLOCK);
                                             return mLCURLY; }
  [^{[:jletter:]](. | mONE_NL)*           {  clearStacks();
                                             return mWRONG_GSTRING_LITERAL; }
}

<IN_TRIPLE_GSTRING> {
  {mGSTRING_TRIPLE_CONTENT}"$"            {  yybegin(GSTRING_STAR_TRIPLE);
                                             return mGSTRING_SINGLE_CONTENT; }
  {mGSTRING_TRIPLE_CONTENT}\"\"\"         {  gStringStack.pop();
                                             if (blockStack.isEmpty()){
                                               yybegin(YYINITIAL);
                                             } else {
                                               yybegin(IN_INNER_BLOCK);
                                             }
                                             return mGSTRING_SINGLE_END; }
  .                                       {  clearStacks();
                                             yybegin(WRONG_STRING);
                                             return mWRONG_GSTRING_LITERAL; }
}

<YYINITIAL> {

"}"                                       {  return(mRCURLY);  }

}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// White spaces & NewLines //////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mWS}                                     {  return mWS; }
{mNLS}                                    {  return mNLS; }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////Comments //////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mSH_COMMENT}                             {  return mSH_COMMENT; }
{mSL_COMMENT}                             {  return mSL_COMMENT; }
{mML_COMMENT}                             {  return mML_COMMENT; }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Integers and floats //////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

{mNUM_INT}                                {  return mNUM_INT; }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Strings & regular expressions ////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// Java strings
{mSTRING_LITERAL}                                          {  return mSTRING_LITERAL; }
{mSINGLE_QUOTED_STRING_BEGIN}                              {  return mWRONG_STRING_LITERAL; }

// GStrings
{mGSTRING_SINGLE_BEGIN}                                    {  yybegin(GSTRING_STAR_SINGLE);
                                                              gStringStack.push(mLPAREN);
                                                              return mGSTRING_SINGLE_BEGIN; }

{mGSTRING_TRIPLE_BEGIN}                                    {  yybegin(GSTRING_STAR_TRIPLE);
                                                              gStringStack.push(mLBRACK);
                                                              return mGSTRING_SINGLE_BEGIN; }

{mGSTRING_LITERAL}                                         {  return mGSTRING_LITERAL; }

\" ([^\""$"] | {mSTRING_ESC})? {mGSTRING_SINGLE_CONTENT}
| \"\"\"[^"$"]                                             {  return mWRONG_GSTRING_LITERAL; }

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// Reserved shorthands //////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

"?"                                       {  return(mQUESTION);  }
"/"                                       {  return(mDIV);  }
"/="                                      {  return(mDIV_ASSIGN);  }
"("                                       {  return(mLPAREN);  }
")"                                       {  return(mRPAREN);  }
"["                                       {  return(mLBRACK);  }
"]"                                       {  return(mRBRACK);  }
"{"                                       {  return(mLCURLY);  }
":"                                       {  return(mCOLON);  }
","                                       {  return(mCOMMA);  }
"."                                       {  return(mDOT);  }
"="                                       {  return(mASSIGN);  }
"<=>"                                     {  return(mCOMPARE_TO);  }
"=="                                      {  return(mEQUAL);  }
"!"                                       {  return(mLNOT);  }
"~"                                       {  return(mBNOT);  }
"!="                                      {  return(mNOT_EQUAL);  }
"+"                                       {  return(mPLUS);  }
"+="                                      {  return(mPLUS_ASSIGN);  }
"++"                                      {  return(mINC);  }
"-"                                       {  return(mMINUS);  }
"-="                                      {  return(mMINUS_ASSIGN);  }
"--"                                      {  return(mDEC);  }
"*"                                       {  return(mSTAR);  }
"*="                                      {  return(mSTAR_ASSIGN);  }
"%"                                       {  return(mMOD);  }
"%="                                      {  return(mMOD_ASSIGN);  }
">>"                                      {  return(mSR);  }
">>="                                     {  return(mSR_ASSIGN);  }
">>>"                                     {  return(mBSR);  }
">>>="                                    {  return(mBSR_ASSIGN);  }
">="                                      {  return(mGE);  }
">"                                       {  return(mGT);  }
"<<"                                      {  return(mSL);  }
"<<="                                     {  return(mSL_ASSIGN);  }
"<="                                      {  return(mLE);  }
"<"                                       {  return(mLT);  }
"^"                                       {  return(mBXOR);  }
"^="                                      {  return(mBXOR_ASSIGN);  }
"|"                                       {  return(mBOR);  }
"|="                                      {  return(mBOR_ASSIGN);  }
"||"                                      {  return(mLOR);  }
"&"                                       {  return(mBAND);  }
"&="                                      {  return(mBAND_ASSIGN);  }
"&&"                                      {  return(mLAND);  }
";"                                       {  return(mSEMI);  }
"$"                                       {  return(mDOLLAR);  }
".."                                      {  return(mRANGE_INCLUSIVE);  }
"..<"                                     {  return(mRANGE_EXCLUSIVE);  }
"..."                                     {  return(mTRIPLE_DOT);  }
"*."                                      {  return(mSPREAD_DOT);  }
"?."                                      {  return(mOPTIONAL_DOT);  }
".&"                                      {  return(mMEMBER_POINTER);  }
"=~"                                      {  return(mREGEX_FIND);  }
"==~"                                     {  return(mREGEX_MATCH);  }
"**"                                      {  return(mSTAR_STAR);  }
"**="                                     {  return(mSTAR_STAR_ASSIGN);  }
"->"                                      {  return(mCLOSABLE_BLOCK_OP);  }
"@"                                       {  return(mAT);  }


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
"any"                                     {  return( kANY );  }
"as"                                      {  return( kAS );  }
"private"                                 {  return( kPRIVATE );  }
"public"                                  {  return( kPUBLIC );  }
"protected"                               {  return( kPROTECTED );  }
"transient"                               {  return( kTRANSIENT );  }
"native"                                  {  return( kNATIVE );  }
"synchronized"                            {  return( kSYNCHRONIZED );  }
"volatile"                                {  return( kVOLATILE );  }
"default"                                 {  return( kDEFAULT );  }
"throws"                                  {  return( kTHROWS );  }
"implements"                              {  return( kIMPLEMENTS );  }
"this"                                    {  return( kTHIS );  }
"if"                                      {  return( kIF );  }
"else"                                    {  return( kELSE );  }
"while"                                   {  return( kWHILE );  }
"with"                                    {  return( kWITH );  }
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
///////////////////////// Other ////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// Unknown symbol is using for debug goals.
.                                         {   return mWRONG; }



