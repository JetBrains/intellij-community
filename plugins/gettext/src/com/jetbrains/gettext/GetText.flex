package com.jetbrains.gettext;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.gettext.GetTextTokenTypes;
import com.jetbrains.gettext.GetTextElementType;

%%

%{

  public _GetTextLexer() {
    this((java.io.Reader)null);
  }

%}

%class _GetTextLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}
%caseless
%ignorecase

EOL = (\r|\n|\r\n)
SPACE = [ \t\f]
WHITE_SPACE = {EOL} | {SPACE}

STRING_TAIL = [^\r\n]*
QUOTED_STRING = ("\"")~(\r|\n|\r\n|"\"")

DOUBLEQUOTE = \"

LBRACE = "["
RBRACE = "]"
NUMBER = [0-9]*
LETTERS = [a-zA-Z]*

COMMENT_SYMBOL = "#"

EXTRACTED_COMMENT = ("."){STRING_TAIL}
REFERENCE_COMMENT = (":"){STRING_TAIL}
PREVIOUS_COMMENT = ("|"){STRING_TAIL}
FLAG_GROUP = (",")

MSGCTXT = "msgctxt"
MSGID = "msgid"
MSGID_PLURAL = "msgid_plural"
MSGSTR = "msgstr"


FUZZY_FLAG = "fuzzy"
FORMAT = "-format"
NO = "no-"
FORMAT_FLAG = ("c"|"objc"|"sh"|"python"|"lisp"|"elisp"|"librep"|"scheme"|"smalltalk"|"java"|
"csharp"|"awk"|"object-pascal"|"ycp"|"tcl"|"perl-brace"|"php"|"gcc-internal"|"gfc-internal"|
"qt"|"qt-plural"|"kde"|"boost") {FORMAT}
NO_FORMAT_FLAG = {NO} {FORMAT_FLAG}
RANGE_FLAG = "range"
DOTS = ".."

FLAG_DELIVERY=","
COLON = ":"

%state START_COMMENT
%state COMMENT
%state EXTR_COMMENT
%state REFERENCE_COMMENT
%state PREVIOUS_COMMENT
%state FLAG_COMMENT
%state FLAG_DEL

%ignorecase

%%

<YYINITIAL> {COMMENT_SYMBOL} { yybegin(START_COMMENT); return GetTextTokenTypes.COMMENT_SYMBOLS;}
<START_COMMENT> {SPACE} { yybegin(COMMENT); return GetTextTokenTypes.COMMENT_SYMBOLS;}
<START_COMMENT> {EXTRACTED_COMMENT} { yybegin(EXTR_COMMENT); return GetTextTokenTypes.EXTR_COMMENT;}
<START_COMMENT> {REFERENCE_COMMENT} { yybegin(REFERENCE_COMMENT); return GetTextTokenTypes.REFERENCE;}
<START_COMMENT> {PREVIOUS_COMMENT} { yybegin(PREVIOUS_COMMENT); return GetTextTokenTypes.PREVIOUS_COMMENT;}
<START_COMMENT> {FLAG_GROUP} { yybegin(FLAG_COMMENT); return GetTextTokenTypes.FLAG_COMMENT;}

<START_COMMENT> {EOL} { yybegin(YYINITIAL); return GetTextTokenTypes.WHITE_SPACE;}
<COMMENT> {EOL} { yybegin(YYINITIAL); return GetTextTokenTypes.WHITE_SPACE;}
<EXTR_COMMENT> {EOL} { yybegin(YYINITIAL); return GetTextTokenTypes.WHITE_SPACE;}
<REFERENCE_COMMENT> {EOL} { yybegin(YYINITIAL); return GetTextTokenTypes.WHITE_SPACE;}
<PREVIOUS_COMMENT> {EOL} { yybegin(YYINITIAL); return GetTextTokenTypes.WHITE_SPACE;}
<FLAG_COMMENT> {EOL} { yybegin(YYINITIAL); return GetTextTokenTypes.WHITE_SPACE;}
<FLAG_DEL> {EOL} { yybegin(YYINITIAL); return GetTextTokenTypes.WHITE_SPACE;}

<COMMENT> {STRING_TAIL} { return GetTextTokenTypes.COMMENT;}
<EXTR_COMMENT> {STRING_TAIL} { return GetTextTokenTypes.EXTR_COMMENT;}
<REFERENCE_COMMENT> {STRING_TAIL} { return GetTextTokenTypes.REFERENCE;}
<PREVIOUS_COMMENT> {STRING_TAIL} { return GetTextTokenTypes.PREVIOUS_COMMENT;}

<FLAG_COMMENT> {FORMAT_FLAG} { yybegin(FLAG_DEL); return GetTextTokenTypes.FORMAT_FLAG;}
<FLAG_COMMENT> {NO_FORMAT_FLAG} { yybegin(FLAG_DEL); return GetTextTokenTypes.NO_FORMAT_FLAG;}
<FLAG_COMMENT> {FUZZY_FLAG} { yybegin(FLAG_DEL); return GetTextTokenTypes.FUZZY_FLAG;}
<FLAG_COMMENT> {RANGE_FLAG} { yybegin(FLAG_DEL); return GetTextTokenTypes.RANGE_FLAG;}

<FLAG_DEL> {FLAG_DELIVERY} { yybegin(FLAG_COMMENT); return GetTextTokenTypes.FLAG_DELIVERY;}
<FLAG_COMMENT, FLAG_DEL> {SPACE} { return GetTextTokenTypes.FLAG_DELIVERY;}
<FLAG_COMMENT, FLAG_DEL> {NUMBER} { return GetTextTokenTypes.RANGE_NUMBER;}
<FLAG_COMMENT, FLAG_DEL> {COLON} { return GetTextTokenTypes.COLON;}
<FLAG_COMMENT, FLAG_DEL> {DOTS} { return GetTextTokenTypes.DOTS;}
<FLAG_COMMENT, FLAG_DEL> [^] { return GetTextTokenTypes.BAD_FLAG_COMMENT; }

{MSGCTXT} { return GetTextTokenTypes.MSGCTXT;}
{MSGCTXT} { return GetTextTokenTypes.MSGCTXT;}
{MSGID} { return GetTextTokenTypes.MSGID;}
{MSGID_PLURAL} { return GetTextTokenTypes.MSGID_PLURAL;}
{MSGSTR} { return GetTextTokenTypes.MSGSTR;}

<YYINITIAL> {LETTERS} { return GetTextTokenTypes.COMMAND;}
<YYINITIAL> {WHITE_SPACE} { return GetTextTokenTypes.WHITE_SPACE;}

{NUMBER} { return GetTextTokenTypes.NUMBER;}
{LBRACE} { return GetTextTokenTypes.LBRACE;}
{RBRACE} { return GetTextTokenTypes.RBRACE;}
{DOUBLEQUOTE} { return GetTextTokenTypes.QUOTE;}

{QUOTED_STRING} { return GetTextTokenTypes.STRING;}

[^] { return GetTextTokenTypes.BAD_CHARACTER; }