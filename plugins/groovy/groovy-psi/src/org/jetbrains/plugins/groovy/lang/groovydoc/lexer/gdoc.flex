// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.groovydoc.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.*;
%%

%class _GroovyDocLexer
%implements FlexLexer
%unicode
%public

%function advance
%type IElementType

%state TOP_LEVEL
%state ASTERISKS
%state AFTER_ASTERISKS
%state COMMENT_DATA
%xstate AFTER_BRACE
%state AFTER_PLAIN_TAG_NAME
%state AFTER_TAG_NAME
%state TAG_VALUE
%state TAG_VALUE_IN_ANGLES
%state TAG_VALUE_IN_PAREN

WS_CHARS = [\ \t\f]
NL_CHARS = [\n\r]
WS_NL_CHARS = {WS_CHARS} | {NL_CHARS}
WS_NL = {WS_NL_CHARS}+
WS = {WS_CHARS}+
DIGIT = [0-9]
ALPHA = [:jletter:]
IDENTIFIER = {ALPHA} ({ALPHA} | {DIGIT} | [":.-"])*
TAG_WITH_VALUE = "param" | "link" | "linkplain" | "see" | "value" | "throws" | "attr"
VALUE_IDENTIFIER = ({ALPHA} | {DIGIT} | [_\."$"\[\]])+
%%

<YYINITIAL> "/**"               { yybegin(AFTER_ASTERISKS); return mGDOC_COMMENT_START; }

<TOP_LEVEL> {
  {WS_NL}                       { return TokenType.WHITE_SPACE; }
  "*"                           { yybegin(ASTERISKS); return mGDOC_ASTERISKS; }
}

<ASTERISKS> {
  "*"                           { return mGDOC_ASTERISKS; }
  [^]                           { yypushback(1); yybegin(AFTER_ASTERISKS); }
}

<AFTER_ASTERISKS, COMMENT_DATA> {
  {WS}                          { return mGDOC_COMMENT_DATA; }
  {NL_CHARS}+{WS_NL_CHARS}*     { yybegin(TOP_LEVEL); return TokenType.WHITE_SPACE; }
}

<TOP_LEVEL, AFTER_ASTERISKS, COMMENT_DATA> {
  "{"                           { yybegin(AFTER_BRACE); return mGDOC_INLINE_TAG_START; }
  "}"                           { yybegin(COMMENT_DATA); return mGDOC_INLINE_TAG_END; }
  .                             { yybegin(COMMENT_DATA); return mGDOC_COMMENT_DATA; }
}

<TOP_LEVEL, AFTER_ASTERISKS, AFTER_BRACE> {
  "@"{TAG_WITH_VALUE}           { yybegin(AFTER_TAG_NAME); return mGDOC_TAG_NAME; }
  "@"{IDENTIFIER}               { yybegin(AFTER_PLAIN_TAG_NAME); return mGDOC_TAG_NAME; }
}

<AFTER_BRACE> [^]               { yypushback(1); yybegin(COMMENT_DATA); }

<AFTER_TAG_NAME> {
  {WS_NL}                       { yybegin(TAG_VALUE); return TokenType.WHITE_SPACE;}
  [^]                           { yypushback(1); yybegin(COMMENT_DATA); }
}

<AFTER_PLAIN_TAG_NAME> {
  {WS}                          { yybegin(COMMENT_DATA); return TokenType.WHITE_SPACE; }
  {NL_CHARS}                    { yybegin(TOP_LEVEL); return TokenType.WHITE_SPACE; }
  "}"                           { yybegin(COMMENT_DATA); return mGDOC_INLINE_TAG_END; }
}

<TAG_VALUE> {
  {WS}                          { yybegin(COMMENT_DATA); return TokenType.WHITE_SPACE; }
  {VALUE_IDENTIFIER}            { return mGDOC_TAG_VALUE_TOKEN; }
  ","                           { return mGDOC_TAG_VALUE_COMMA; }
  "<"{IDENTIFIER}">"            { yybegin(COMMENT_DATA); return mGDOC_TAG_VALUE_TOKEN; }
  "("                           { yybegin(TAG_VALUE_IN_PAREN); return mGDOC_TAG_VALUE_LPAREN; }
  "#"                           { return mGDOC_TAG_VALUE_SHARP_TOKEN; }
  [^]                           { yypushback(1); yybegin(COMMENT_DATA); }
}

<TAG_VALUE_IN_PAREN> {
  {WS_NL}                       { return TokenType.WHITE_SPACE; }
  {VALUE_IDENTIFIER}            { return mGDOC_TAG_VALUE_TOKEN; }
  ","                           { return mGDOC_TAG_VALUE_COMMA; }
  ")"                           { yybegin(TAG_VALUE); return mGDOC_TAG_VALUE_RPAREN; }
}

"*/"                            { return mGDOC_COMMENT_END; }
[^]                             { return mGDOC_COMMENT_DATA; }
