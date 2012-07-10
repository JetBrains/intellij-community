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
NO_EOL = !(\r|\n|\r\n)
SPACE = [ \t\f]
WHITE_SPACE = {EOL} | {SPACE}

STRING_TAIL = [^\r\n]*
QUOTED_STRING = ("\"")~(\r|\n|\r\n|"\"")
TMP_QUOTED_STRING = ("\"")~("\"")

DOUBLEQUOTE = \"
DOUBLEQUOTED_STRING = ([^\"] | \"\" | \\\")+

LBRACE = "["
RBRACE = "]"
NUMBER = [0-9]*

COMMENT_SYMBOL = "#"

COMMENT_TYPE_SYMBOLS = ("#."|"#:"|"#,"|"#|")
COMMENT = (" "|\t|\f)~{EOL}
EXTRACTED_COMMENT = (".")~{EOL}
REFERENCE_COMMENT = (":")~{EOL}
PREVIOUS_COMMENT = ("|")~{EOL}
FLAG_GROUP = (",")

DOT = "."
COLON = ":"
LINE = "|"
COMMA = ","

MSGCTXT = "msgctxt"
MSGID = "msgid"
MSGID_PLURAL = "msgid_plural"
MSGSTR = "msgstr"


FUZZY_FLAG = "fuzzy"
NO = "no-"
FORMAT = "-format"
C = "c"
OBJC = "objc"
SH = "sh"
PYTHON = "python"
LISP = "lisp"
ELISP = "elisp"
LIBREP = "librep"
SCHEME = "scheme"
SMALLTALK = "smalltalk"
JAVA = "java"
CSHARP = "csharp"
AWK = "awk"
YCP = "ycp"
TCL = "tcl"
PERL = "perl-brace"
PHP = "php"
GCC = "gcc-internal"
GFC = "gfc-internal"
QT = "qt"
KDE = "kde"
BOOST = "boost"
OBJECT_PASCAL = "object-pascal"
QT_PLURAL = "qt-plural"

PASCAL_FORMAT_FLAG = "object-pascal-format"
NO_PASCAL_FORMAT_FLAG = "no-object-pascal-format"
QT_FORMAT_FLAG = "qt-plural-format"
NO_QT_FORMAT_FLAG = "no-qt-plural-format"
FORMAT_FLAG = (C|OBJC|SH|PYTHON|LISP|EISP|LIBREP|SCHEME|SMALLTALK|JAVA|SCHARP|AWK|OBJECT_PASCAL|YCP|TCL|PERL|PHP|GCC|GFC|QT|QT_PLURAL|KDE|BOOST) {FORMAT}
NO_FORMAT_FLAG = {NO} {FORMAT_FLAG}

RANGE_FLAG = "range"
DOTS = ".."

FLAG_DELIVERY=","

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
<START_COMMENT> {DOT} { yybegin(EXTR_COMMENT); return GetTextTokenTypes.COMMENT_SYMBOLS;}
<START_COMMENT> {COLON} { yybegin(REFERENCE_COMMENT); return GetTextTokenTypes.COMMENT_SYMBOLS;}
<START_COMMENT> {LINE} { yybegin(PREVIOUS_COMMENT); return GetTextTokenTypes.COMMENT_SYMBOLS;}
<START_COMMENT> {FLAG_GROUP} { yybegin(FLAG_COMMENT); return GetTextTokenTypes.COMMENT_SYMBOLS;}

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

<FLAG_COMMENT> {PASCAL_FORMAT_FLAG} { yybegin(FLAG_DEL); return GetTextTokenTypes.FORMAT_FLAG;}
<FLAG_COMMENT> {NO_PASCAL_FORMAT_FLAG} { yybegin(FLAG_DEL); return GetTextTokenTypes.NO_FORMAT_FLAG;}
<FLAG_COMMENT> {QT_FORMAT_FLAG} { yybegin(FLAG_DEL); return GetTextTokenTypes.FORMAT_FLAG;}
<FLAG_COMMENT> {NO_QT_FORMAT_FLAG} { yybegin(FLAG_DEL); return GetTextTokenTypes.NO_FORMAT_FLAG;}
<FLAG_COMMENT> {FORMAT_FLAG} { yybegin(FLAG_DEL); return GetTextTokenTypes.FORMAT_FLAG;}
<FLAG_COMMENT> {NO_FORMAT_FLAG} { yybegin(FLAG_DEL); return GetTextTokenTypes.NO_FORMAT_FLAG;}
<FLAG_COMMENT> {FUZZY_FLAG} { yybegin(FLAG_DEL); return GetTextTokenTypes.FUZZY_FLAG;}
<FLAG_COMMENT> {RANGE_FLAG} { yybegin(FLAG_DEL); return GetTextTokenTypes.RANGE_FLAG;}

<FLAG_DEL> {FLAG_DELIVERY} { yybegin(FLAG_COMMENT); return GetTextTokenTypes.FLAG_DELIVERY;}

{MSGCTXT} { return GetTextTokenTypes.MSGCTXT;}
{MSGID} { return GetTextTokenTypes.MSGID;}
{MSGID_PLURAL} { return GetTextTokenTypes.MSGID_PLURAL;}
{MSGSTR} { return GetTextTokenTypes.MSGSTR;}

<YYINITIAL> {WHITE_SPACE} { return GetTextTokenTypes.WHITE_SPACE;}
<FLAG_COMMENT, FLAG_DEL> {SPACE} { return GetTextTokenTypes.WHITE_SPACE;}
{NUMBER} { return GetTextTokenTypes.NUMBER;}
{LBRACE} { return GetTextTokenTypes.LBRACE;}
{RBRACE} { return GetTextTokenTypes.RBRACE;}
{COLON} { return GetTextTokenTypes.COLON;}
{DOTS} { return GetTextTokenTypes.DOTS;}

{QUOTED_STRING} { return GetTextTokenTypes.STRING;}

[^] { return GetTextTokenTypes.BAD_CHARACTER; }