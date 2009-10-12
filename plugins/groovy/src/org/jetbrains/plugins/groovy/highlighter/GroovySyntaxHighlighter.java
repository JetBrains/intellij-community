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

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
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
      mML_COMMENT
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

  static final TokenSet tWRONG_REGEX = TokenSet.create(
      mWRONG_REGEX_LITERAL
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

  static final TokenSet tREGEXP = TokenSet.create(
      mREGEX_LITERAL,

      mREGEX_BEGIN,
      mREGEX_CONTENT,
      mREGEX_END

  );

  static final TokenSet tBRACES = TokenSet.create(
      mLPAREN,
      mRPAREN,
      mLBRACK,
      mRBRACK,
      mLCURLY,
      mRCURLY
  );

  public static final TokenSet tOPERATORS = TokenSet.create(
      mQUESTION,
      mCOMPARE_TO,
      mEQUAL,
      mBNOT,
      mNOT_EQUAL,
      mPLUS,
      mPLUS_ASSIGN,
      mINC,
      mMINUS,
      mMINUS_ASSIGN,
      mDEC,
      mSTAR,
      mSTAR_ASSIGN,
      mMOD,
      mMOD_ASSIGN,
      mSR_ASSIGN,
      mBSR_ASSIGN,
      mGE,
      mGT,
      mSL_ASSIGN,
      mLE,
      mLT,
      mBXOR,
      mBXOR_ASSIGN,
      mBOR,
      mBOR_ASSIGN,
      mLOR,
      mBAND,
      mBAND_ASSIGN,
      mLAND,
      mDOLLAR,
      mRANGE_INCLUSIVE,
      mRANGE_EXCLUSIVE,
      mTRIPLE_DOT,
      mSPREAD_DOT,
      mOPTIONAL_DOT,
      mMEMBER_POINTER,
      mREGEX_FIND,
      mREGEX_MATCH,
      mSTAR_STAR,
      mSTAR_STAR_ASSIGN,
      mCLOSABLE_BLOCK_OP,
      mAT
  );

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
    fillMap(ATTRIBUTES, tREGEXP, DefaultHighlighter.REGEXP);
    fillMap(ATTRIBUTES, tWRONG_REGEX, DefaultHighlighter.REGEXP);
    fillMap(ATTRIBUTES, tBRACES, DefaultHighlighter.BRACES);
  }

  @NotNull
  public Lexer getHighlightingLexer() {
    return new GroovyLexer();
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(ATTRIBUTES.get(tokenType));
  }
}
