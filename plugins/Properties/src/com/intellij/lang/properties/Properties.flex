/* It's an automatically generated code. Do not modify it. */
package com.intellij.lang.properties;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

%%

%class _PropertiesLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

WHITE_SPACE_CHAR=[\ \n\r\t\f]
VALUE_CHARACTERS=[^\ \n\r\t\f]+
KEY_CHARACTERS=[^:=\ \n\r\t\f]+
KEY_VALUE_SEPARATOR=[:=]
WHITE_SPACE_WITH_NEW_LINE=[\ \t\f]*[\n\r][\ \n\r\t\f]*
END_OF_LINE_COMMENT=("#"|"!")[^\r\n]*

%state IN_VALUE

%%

{END_OF_LINE_COMMENT} { return PropertiesTokenTypes.END_OF_LINE_COMMENT; }

<YYINITIAL> {KEY_CHARACTERS}        { yybegin(YYINITIAL); return PropertiesTokenTypes.KEY_CHARACTERS; }
<YYINITIAL> {KEY_VALUE_SEPARATOR}   { yybegin(IN_VALUE); return PropertiesTokenTypes.KEY_VALUE_SEPARATOR; }
<IN_VALUE> {VALUE_CHARACTERS}       { yybegin(IN_VALUE); return PropertiesTokenTypes.VALUE_CHARACTERS; }
<IN_VALUE> {WHITE_SPACE_WITH_NEW_LINE}   { yybegin(YYINITIAL); return PropertiesTokenTypes.WHITE_SPACE; }

{WHITE_SPACE_CHAR}+   { return PropertiesTokenTypes.WHITE_SPACE; }
