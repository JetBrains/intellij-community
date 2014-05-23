/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.groovydoc.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;

%%

%class _GroovyDocLexer
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

%{ // User code

  public _GroovyDocLexer() {
    this((java.io.Reader)null);
  }

  public boolean checkAhead(char c) {
     if (zzMarkedPos >= zzBuffer.length()) return false;
     return zzBuffer.charAt(zzMarkedPos) == c;
  }

  public void goTo(int offset) {
    zzCurrentPos = zzMarkedPos = zzStartRead = offset;
    zzPushbackPos = 0;
    zzAtEOF = offset < zzEndRead;
  }


%}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////// GroovyDoc lexems ////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

%state COMMENT_DATA_START
%state COMMENT_DATA
%state TAG_DOC_SPACE
%state PARAM_TAG_SPACE
%state DOC_TAG_VALUE
%state DOC_TAG_VALUE_IN_PAREN
%state DOC_TAG_VALUE_IN_LTGT
%state INLINE_TAG_NAME

WHITE_DOC_SPACE_CHAR=[\ \t\f\n\r]
WHITE_DOC_SPACE_NO_NL=[\ \t\f]
DIGIT=[0-9]
ALPHA=[:jletter:]
IDENTIFIER={ALPHA}({ALPHA}|{DIGIT}|[":.-"])*

%%

<YYINITIAL> "/**"                                                     { yybegin(COMMENT_DATA_START);
                                                                        return GroovyDocTokenTypes.mGDOC_COMMENT_START; }
<COMMENT_DATA_START> {WHITE_DOC_SPACE_CHAR}+                          { return TokenType.WHITE_SPACE; }
<COMMENT_DATA>  {WHITE_DOC_SPACE_NO_NL}+                              { return GroovyDocTokenTypes.mGDOC_COMMENT_DATA; }
<COMMENT_DATA>  [\n\r]+{WHITE_DOC_SPACE_CHAR}*                        { return TokenType.WHITE_SPACE; }

<DOC_TAG_VALUE> {WHITE_DOC_SPACE_CHAR}+                               { yybegin(COMMENT_DATA);
                                                                        return TokenType.WHITE_SPACE; }
<DOC_TAG_VALUE, DOC_TAG_VALUE_IN_PAREN> ({ALPHA}|[_0-9\."$"\[\]])+    { return GroovyDocTokenTypes.mGDOC_TAG_VALUE_TOKEN; }
<DOC_TAG_VALUE> [\(]                                                  { yybegin(DOC_TAG_VALUE_IN_PAREN);
                                                                        return GroovyDocTokenTypes.mGDOC_TAG_VALUE_LPAREN; }
<DOC_TAG_VALUE_IN_PAREN> [\)]                                         { yybegin(DOC_TAG_VALUE);
                                                                        return GroovyDocTokenTypes.mGDOC_TAG_VALUE_RPAREN; }
<DOC_TAG_VALUE> [#]                                                   { return GroovyDocTokenTypes.mGDOC_TAG_VALUE_SHARP_TOKEN; }
<DOC_TAG_VALUE, DOC_TAG_VALUE_IN_PAREN> [,]                           { return GroovyDocTokenTypes.mGDOC_TAG_VALUE_COMMA; }
<DOC_TAG_VALUE_IN_PAREN> {WHITE_DOC_SPACE_CHAR}+                      { return TokenType.WHITE_SPACE; }

<INLINE_TAG_NAME, COMMENT_DATA_START> "@param"                        { yybegin(PARAM_TAG_SPACE);
                                                                        return GroovyDocTokenTypes.mGDOC_TAG_NAME; }
<PARAM_TAG_SPACE>  {WHITE_DOC_SPACE_CHAR}+                            { yybegin(DOC_TAG_VALUE);
                                                                        return TokenType.WHITE_SPACE;}
<DOC_TAG_VALUE> [\<]                                                  { yybegin(DOC_TAG_VALUE_IN_LTGT);
                                                                        return GroovyDocTokenTypes.mGDOC_TAG_VALUE_LT; }
<DOC_TAG_VALUE_IN_LTGT> {IDENTIFIER}                                  { return GroovyDocTokenTypes.mGDOC_TAG_VALUE_TOKEN; }
<DOC_TAG_VALUE_IN_LTGT> [\>]                                          { yybegin(COMMENT_DATA);
                                                                        return GroovyDocTokenTypes.mGDOC_TAG_VALUE_GT; }

<COMMENT_DATA_START, COMMENT_DATA> "{"                                { if (checkAhead('@')){
                                                                          yybegin(INLINE_TAG_NAME);
                                                                        }
                                                                        else{
                                                                          yybegin(COMMENT_DATA);
                                                                        }
                                                                        return GroovyDocTokenTypes.mGDOC_INLINE_TAG_START;
                                                                      }

<INLINE_TAG_NAME> "@"{IDENTIFIER}                                     { yybegin(TAG_DOC_SPACE);
                                                                        return GroovyDocTokenTypes.mGDOC_TAG_NAME; }
<COMMENT_DATA_START, COMMENT_DATA, TAG_DOC_SPACE, DOC_TAG_VALUE> "}"  { yybegin(COMMENT_DATA);
                                                                        return GroovyDocTokenTypes.mGDOC_INLINE_TAG_END; }


<COMMENT_DATA_START, COMMENT_DATA, DOC_TAG_VALUE> .                   { yybegin(COMMENT_DATA);
                                                                        return GroovyDocTokenTypes.mGDOC_COMMENT_DATA; }
<COMMENT_DATA_START> "@"{IDENTIFIER}                                  { yybegin(TAG_DOC_SPACE);
                                                                        return GroovyDocTokenTypes.mGDOC_TAG_NAME;  }
<TAG_DOC_SPACE>  {WHITE_DOC_SPACE_CHAR}+                              { if (checkAhead('<') || checkAhead('\"')) {
                                                                          yybegin(COMMENT_DATA);
                                                                        }
                                                                        else if (checkAhead('\u007b') ) {
                                                                          yybegin(COMMENT_DATA); //lbrace -  there's some error in JLex when typing lbrace directly
                                                                        }
                                                                        else {
                                                                          yybegin(DOC_TAG_VALUE);
                                                                        }
                                                                        return TokenType.WHITE_SPACE;
                                                                      }

"*"+"/"                                                               { return GroovyDocTokenTypes.mGDOC_COMMENT_END; }
[^]                                                                   { return GroovyDocTokenTypes.mGDOC_COMMENT_BAD_CHARACTER; }