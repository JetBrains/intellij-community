/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
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

package org.jetbrains.plugins.groovy.highlighter;

import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * @author ilyas
 */
public class GroovySyntaxHighlighter extends SyntaxHighlighterBase implements GroovyTokenTypes {

  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<IElementType, TextAttributesKey>();


  static final TokenSet tBLOCK_COMMENTS = TokenSet.create(
      mML_COMMENT, GROOVY_DOC_COMMENT
  );

  static final TokenSet tNUMBERS = TokenSet.create(
      mNUM_INT,
      mNUM_BIG_DECIMAL,
      mNUM_BIG_INT,
      mNUM_DOUBLE,
      mNUM_FLOAT,
      mNUM_LONG

  );

  static final TokenSet tLINE_COMMENTS = TokenSet.create(
      mSL_COMMENT,
      mSH_COMMENT
  );

  static final TokenSet tBAD_CHARACTERS = TokenSet.create(
      mWRONG
  );

  static final TokenSet tGSTRINGS = TokenSet.create(
      mGSTRING_BEGIN,
      mGSTRING_CONTENT,
      mGSTRING_END,
      mGSTRING_LITERAL
  );

  static final TokenSet tSTRINGS = TokenSet.create(
      mSTRING_LITERAL
  );

  static final TokenSet tBRACES = TokenSet.create(
    mLCURLY,
    mRCURLY
  );
  static final TokenSet tPARENTHESES = TokenSet.create(
    mLPAREN,
    mRPAREN
  );
  static final TokenSet tBRACKETS = TokenSet.create(
    mLBRACK,
    mRBRACK
  );

  //public static final TokenSet tOPERATORS = TokenSet.create(
  //    mQUESTION,
  //    mCOMPARE_TO,
  //    mEQUAL,
  //    mBNOT,
  //    mNOT_EQUAL,
  //    mPLUS,
  //    mPLUS_ASSIGN,
  //    mINC,
  //    mMINUS,
  //    mMINUS_ASSIGN,
  //    mDEC,
  //    mSTAR,
  //    mSTAR_ASSIGN,
  //    mMOD,
  //    mMOD_ASSIGN,
  //    mSR_ASSIGN,
  //    mBSR_ASSIGN,
  //    mGE,
  //    mGT,
  //    mSL_ASSIGN,
  //    mLE,
  //    mLT,
  //    mBXOR,
  //    mBXOR_ASSIGN,
  //    mBOR,
  //    mBOR_ASSIGN,
  //    mLOR,
  //    mBAND,
  //    mBAND_ASSIGN,
  //    mLAND,
  //    mDOLLAR,
  //    mRANGE_INCLUSIVE,
  //    mRANGE_EXCLUSIVE,
  //    mTRIPLE_DOT,
  //    mSPREAD_DOT,
  //    mOPTIONAL_DOT,
  //    mMEMBER_POINTER,
  //    mREGEX_FIND,
  //    mREGEX_MATCH,
  //    mSTAR_STAR,
  //    mSTAR_STAR_ASSIGN,
  //    mCLOSABLE_BLOCK_OP,
  //    mAT
  //);
  //
  public static final TokenSet tKEYWORDS = TokenSet.create(
      kPACKAGE,
      kIMPORT,
      kSTATIC,
      kSTRICTFP,
      kDEF,
      kCLASS,
      kINTERFACE,
      kENUM,
      kEXTENDS,
      kSUPER,
      kVOID,
      kBOOLEAN,
      kBYTE,
      kCHAR,
      kSHORT,
      kINT,
      kFLOAT,
      kLONG,
      kDOUBLE,
      kAS,
      kPRIVATE,
      kPUBLIC,
      kPROTECTED,
      kABSTRACT,
      kTRANSIENT,
      kNATIVE,
      kSYNCHRONIZED,
      kVOLATILE,
      kDEFAULT,
      kDO,
      kTHROWS,
      kIMPLEMENTS,
      kTHIS,
      kIF,
      kELSE,
      kWHILE,
      kSWITCH,
      kFOR,
      kIN,
      kRETURN,
      kBREAK,
      kCONTINUE,
      kTHROW,
      kASSERT,
      kCASE,
      kTRY,
      kFINALLY,
      kFINAL,
      kCATCH,
      kINSTANCEOF,
      kNEW,
      kTRUE,
      kFALSE,
      kNULL
  );

  static {
    fillMap(ATTRIBUTES, tLINE_COMMENTS, DefaultHighlighter.LINE_COMMENT);
    fillMap(ATTRIBUTES, tBLOCK_COMMENTS, DefaultHighlighter.BLOCK_COMMENT);
    fillMap(ATTRIBUTES, tBAD_CHARACTERS, DefaultHighlighter.BAD_CHARACTER);
    fillMap(ATTRIBUTES, tKEYWORDS, DefaultHighlighter.KEYWORD);
    fillMap(ATTRIBUTES, tNUMBERS, DefaultHighlighter.NUMBER);
    fillMap(ATTRIBUTES, tGSTRINGS, DefaultHighlighter.GSTRING);
    fillMap(ATTRIBUTES, tSTRINGS, DefaultHighlighter.STRING);
    fillMap(ATTRIBUTES, DefaultHighlighter.STRING, mREGEX_BEGIN, mREGEX_CONTENT, mREGEX_END, mDOLLAR_SLASH_REGEX_BEGIN, mDOLLAR_SLASH_REGEX_CONTENT, mDOLLAR_SLASH_REGEX_END);
    fillMap(ATTRIBUTES, tBRACES, DefaultHighlighter.BRACES);
    fillMap(ATTRIBUTES, tBRACKETS, DefaultHighlighter.BRACKETS);
    fillMap(ATTRIBUTES, tPARENTHESES, DefaultHighlighter.PARENTHESES);
    fillMap(ATTRIBUTES, DefaultHighlighter.VALID_STRING_ESCAPE, StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN);
    fillMap(ATTRIBUTES, DefaultHighlighter.INVALID_STRING_ESCAPE, StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN);
    fillMap(ATTRIBUTES, DefaultHighlighter.INVALID_STRING_ESCAPE, StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN);
  }

  @NotNull
  public Lexer getHighlightingLexer() {
    return new GroovyHighlightingLexer();
  }

  private static class GroovyHighlightingLexer extends LayeredLexer {
    private GroovyHighlightingLexer() {
      super(new GroovyLexer());
      registerSelfStoppingLayer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, GroovyTokenTypes.mSTRING_LITERAL, true, "$"),
                                new IElementType[]{GroovyTokenTypes.mSTRING_LITERAL}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, GroovyTokenTypes.mGSTRING_LITERAL, true, "$"),
                                new IElementType[]{GroovyTokenTypes.mGSTRING_LITERAL}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, GroovyTokenTypes.mGSTRING_CONTENT, true, "$"),
                                new IElementType[]{GroovyTokenTypes.mGSTRING_CONTENT}, IElementType.EMPTY_ARRAY);
      registerSelfStoppingLayer(new GroovySlashyStringLexer(), new IElementType[]{GroovyTokenTypes.mREGEX_CONTENT},
                                IElementType.EMPTY_ARRAY);
    }
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(ATTRIBUTES.get(tokenType));
  }
}
