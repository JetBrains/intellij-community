// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.devkit.apiDump.lang.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.lexer.FlexLexer;
import com.intellij.devkit.apiDump.lang.elementTypes.ADTokenType;
import com.intellij.devkit.apiDump.lang.psi.ADElementTypes;
import com.intellij.psi.TokenType;
@SuppressWarnings("ALL")
%%

%{
  public _ADLexer() {
    this((java.io.Reader)null);
  }
%}

%unicode
%class _ADLexer
%implements FlexLexer
%function advance
%type IElementType

IDENTIFIER = [:jletter:] [:jletterdigit:]*
EOL=[\n\r]
SPACE=[ \u0009\u000B\u000C\u0000]
WHITE_SPACE={EOL}|{SPACE}
%%

/*
 * NOTE: the rule set does not include rules for whitespaces, comments, and text literals -
 * they are implemented in com.intellij.lang.java.lexer.JavaLexer class.
 */

<YYINITIAL> {
  {IDENTIFIER}        { return ADElementTypes.IDENTIFIER; }
  "<"                 { return ADElementTypes.LESS; }
  ">"                 { return ADElementTypes.MORE; }
  "("                 { return ADElementTypes.LPAREN; }
  ")"                 { return ADElementTypes.RPAREN; }
  "["                 { return ADElementTypes.LBRACKET; }
  "]"                 { return ADElementTypes.RBRACKET; }
  ","                 { return ADElementTypes.COMMA; }
  "."                 { return ADElementTypes.DOT; }
  "*"                 { return ADElementTypes.ASTERISK; }
  ":"                 { return ADElementTypes.COLON; }
  "-"                 { return ADElementTypes.MINUS; }
  "@"                 { return ADElementTypes.AT; }
  {WHITE_SPACE}+      { return TokenType.WHITE_SPACE; }
  [^]                 { return TokenType.BAD_CHARACTER; }
}
