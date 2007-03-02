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

%}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// NewLines and spaces /////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

mONE_NL = \r | \n | \r\n | \u0085| \u2028 | \u2029          // NewLines
mWS = " " | \t | \f | \\                                    // Whitespaces

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// Comments ////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

C_STYLE_COMMENT=("/*" [^"*"] {COMMENT_TAIL} ) | "/*"
DOC_COMMENT="/*" "*"+ ( "/" | ( [^"/""*"] {COMMENT_TAIL} ) )?
COMMENT_TAIL=( [^"*"]* ("*"+ [^"*""/"] )? )* ("*"+"/")?

mSH_COMMENT = "#!"[^\r\n]*
mSL_COMMENT = "/""/"[^\r\n]*
mML_COMMENT = C_STYLE_COMMENT | DOC_COMMENT

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      integers and floats     /////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/*
integerLiteral = ({decimalNumeral} | {hexNumeral} | {octalNumeral}) (L | l)?
decimalNumeral = 0 | {nonZeroDigit} {digit}*
hexNumeral = 0 x {hexDigit}+
octalNumeral = 0{octalDigit}+
digit = [0-9]
nonZeroDigit = [1-9]
octalDigit = [0-7]
hexDigit = [0-9A-Fa-f]

floatingPointLiteral =
        {digit} + "." {digit}* {exponentPart}? {floatType}?
    | "." {digit}+ {exponentPart}? {floatType}?
    | {digit}+ {exponentPart} {floatType}?
    | {digit}+ {exponentPart}? {floatType}

exponentPart = (E | e) ("+" | "-")? {digit}+
floatType = F | f | D | d
*/

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////      identifiers      ////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/*

identifier = {plainid} //| "'" "\"" {stringLiteral} "\"" "'"

digit = [0-9]
special =   \u0021 | \u0023
          | [\u0025-\u0026]
          | [\u002A-\u002B]
          | \u002D | \u005E
          | \u003A
          | [\u003C-\u0040]
          | \u007E
          | \u005C | \u002F     //slashes

// Vertical line haemorrhoids
op = \u007C ({special} | \u007C)+
     | {special} ({special} | \u007C)*

idrest1 = [:jletter:]? [:jletterdigit:]* ("_" {op})?
idrest = [:jletter:]? [:jletterdigit:]* ("_" {op} | "_" {idrest1} )?
varid = [:jletter:] {idrest}

plainid = {varid}
          | {op}

*/          

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// String & chars //////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/*

ESCAPE_SEQUENCE=\\[^\r\n]
CHARACTER_LITERAL="'"([^\\\'\r\n]|{ESCAPE_SEQUENCE})*("'"|\\)
STRING_LITERAL=\"([^\\\"\r\n]|{ESCAPE_SEQUENCE})*(\"|\\)? |
                \"\"\" ( (\"(\")?)? [^\"] )* \"\"\"    // Multi-line string

charEscapeSeq = \\[^\r\n]
charNoDoubleQuote = !( ![^"\""] | {LineTerminator})
stringElement = {charNoDoubleQuote} | {charEscapeSeq}  
stringLiteral = {stringElement}*
symbolLiteral = "\'" {plainid}

*/

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////  states ///////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%%

<YYINITIAL>{

// White spaces & NewLines
{mWS}                                                     {   return WS; }

// Comments
{mSH_COMMENT}                                             {   return SH_COMMENT; }
{mSL_COMMENT}                                             {   return SL_COMMENT; }
{mML_COMMENT}                                             {   return ML_COMMENT; }

// Other
. | {mONE_NL}                                             {   return WRONG; }

}



////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
/////////////////////////////// FOR ALL INCLUSIVE STATES //////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/*

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
///////////////////////// keywords /////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

"abstract"                              {   return process(kABSTRACT); }

"case" / ({LineTerminator}|{WhiteSpaceInLine})+("class" | "object")
                                        {   return process(kCASE); }

"case"                                  {   braceStack.push(kCASE);
                                            yybegin(NEW_LINE_DEPRECATED);
                                            return process(kCASE); }
                                            
"catch"                                 {   return process(kCATCH); }
"class"                                 {   return process(kCLASS); }
"def"                                   {   return process(kDEF); }
"do"                                    {   return process(kDO); }
"else"                                  {   return process(kELSE); }
"extends"                               {   return process(kEXTENDS); }
"false"                                 {   processNewLine();
                                            return process(kFALSE); }
"final"                                 {   return process(kFINAL); }
"finally"                               {   return process(kFINALLY); }
"for"                                   {   return process(kFOR); }
"if"                                    {   return process(kIF); }
"implicit"                              {   return process(kIMPLICIT); }
"import"                                {   return process(kIMPORT); }
"match"                                 {   return process(kMATCH); }
"new"                                   {   return process(kNEW); }
"null"                                  {   processNewLine();
                                            return process(kNULL); }
"object"                                {   return process(kOBJECT); }
"override"                              {   return process(kOVERRIDE); }
"package"                               {   return process(kPACKAGE); }
"private"                               {   return process(kPRIVATE); }
"protected"                             {   return process(kPROTECTED); }
"requires"                              {   return process(kREQUIRES); }
"return"                                {   processNewLine();
                                            return process(kRETURN); }
"sealed"                                {   return process(kSEALED); }
"super"                                 {   return process(kSUPER); }
"this"                                  {   processNewLine();
                                            return process(kTHIS); }
"throw"                                 {   return process(kTHROW); }
"trait"                                 {   return process(kTRAIT); }
"try"                                   {   return process(kTRY); }
"true"                                  {   processNewLine();
                                            return process(kTRUE); }
"type"                                  {   processNewLine();
                                            return process(kTYPE); }
"val"                                   {   return process(kVAL); }
"var"                                   {   return process(kVAR); }
"while"                                 {   return process(kWHILE); }
"with"                                  {   return process(kWITH); }
"yield"                                 {   return process(kYIELD); }

///////////////////// Reserved shorthands //////////////////////////////////////////

"*"                                     {   processNewLine();
                                            return process(tIDENTIFIER);  }
"?"                                     {   processNewLine();
                                            return process(tIDENTIFIER);  }

"_"                                     {   processNewLine();
                                            return process(tUNDER);  }
":"                                     {   return process(tCOLON);  }
"="                                     {   return process(tASSIGN);  }
"=>"                                    {   return popBraceStack(tFUNTYPE); }
\u21D2                                  {   return process(tFUNTYPE_ASCII); }
"<-"                                    {   return process(tCHOOSE); }
"<:"                                    {   return process(tUPPER_BOUND); }
">:"                                    {   return process(tLOWER_BOUND); }
"<%"                                    {   return process(tVIEW); }
"#"                                     {   return process(tINNER_CLASS); }
"@"                                     {   return process(tAT);}

//"&"                                     {   return process(tAND);}
//"|"                                     {   return process(tOR);}

"&"                                     {   processNewLine();
                                            return process(tIDENTIFIER);}
"|"                                     {   processNewLine();
                                            return process(tIDENTIFIER);}
"+"                                     {   processNewLine();
                                            return process(tIDENTIFIER); }
"-"                                     {   processNewLine();
                                            return process(tIDENTIFIER);}
"~"                                     {   processNewLine();
                                            return process(tIDENTIFIER);}
"!"                                     {   processNewLine();
                                            return process(tIDENTIFIER);}

"."                                     {   return process(tDOT);}
";"                                     {   return process(tSEMICOLON);}
","                                     {   return process(tCOMMA);}


////////////////////// Identifier /////////////////////////////////////////

{identifier}                            {   processNewLine();
                                            return process(tIDENTIFIER); }
({digit}+) / ("." {identifier})         {   processNewLine();
                                            return process(tINTEGER);  }
{integerLiteral}                        {   processNewLine();
                                            return process(tINTEGER);  }
{floatingPointLiteral}                  {   processNewLine();
                                            return process(tFLOAT);      }

////////////////////// white spaces in line ///////////////////////////////////////////////
{WhiteSpaceInLine}                            {   return process(tWHITE_SPACE_IN_LINE);  }

////////////////////// white spaces line terminator ///////////////////////////////////////////////
//{LineTerminator}                              {   return process(tNON_SIGNIFICANT_NEWLINE); }
{LineTerminator}                              {   return process(tWHITE_SPACE_IN_LINE); }

////////////////////// STUB ///////////////////////////////////////////////
*/



