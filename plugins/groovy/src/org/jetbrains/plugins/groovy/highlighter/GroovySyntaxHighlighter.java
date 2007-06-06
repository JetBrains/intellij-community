/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.lexer.GroovyFlexLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;

import java.util.HashMap;
import java.util.Map;

/**
 * @author ilyas
 */
public class GroovySyntaxHighlighter extends SyntaxHighlighterBase implements GroovyTokenTypes {

  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<IElementType, TextAttributesKey>();


  static final TokenSet tCOMMENTS = TokenSet.create(
      GroovyTokenTypes.mSL_COMMENT,
      GroovyTokenTypes.mML_COMMENT,
      GroovyTokenTypes.mSH_COMMENT
  );

  static final TokenSet tNUMBERS = TokenSet.create(
      GroovyTokenTypes.mNUM_INT,
      GroovyTokenTypes.mNUM_BIG_DECIMAL,
      GroovyTokenTypes.mNUM_BIG_INT,
      GroovyTokenTypes.mNUM_DOUBLE,
      GroovyTokenTypes.mNUM_FLOAT,
      GroovyTokenTypes.mNUM_LONG

  );

  static final TokenSet tBAD_CHARACTERS = TokenSet.create(
      GroovyTokenTypes.mWRONG
  );

  static final TokenSet tWRONG_STRING = TokenSet.create(
      GroovyTokenTypes.mWRONG_GSTRING_LITERAL,
      GroovyTokenTypes.mWRONG_STRING_LITERAL
  );

  static final TokenSet tWRONG_REGEX = TokenSet.create(
      GroovyTokenTypes.mWRONG_REGEX_LITERAL
  );

  static final TokenSet tSTRINGS = TokenSet.create(
      GroovyTokenTypes.mSTRING_LITERAL,

      GroovyTokenTypes.mGSTRING_SINGLE_BEGIN,
      GroovyTokenTypes.mGSTRING_SINGLE_CONTENT,
      GroovyTokenTypes.mGSTRING_SINGLE_END,
      GroovyTokenTypes.mGSTRING_LITERAL
  );

  static final TokenSet tREGEXP = TokenSet.create(
      GroovyTokenTypes.mREGEX_LITERAL,

      GroovyTokenTypes.mREGEX_BEGIN,
      GroovyTokenTypes.mREGEX_CONTENT,
      GroovyTokenTypes.mREGEX_END

  );

  static final TokenSet tBRACES = TokenSet.create(
      GroovyTokenTypes.mLPAREN,
      GroovyTokenTypes.mRPAREN,
      GroovyTokenTypes.mLBRACK,
      GroovyTokenTypes.mRBRACK,
      GroovyTokenTypes.mLCURLY,
      GroovyTokenTypes.mRCURLY
  );

  public static final TokenSet tOPERATORS = TokenSet.create(
      GroovyTokenTypes.mQUESTION,
      GroovyTokenTypes.mCOMPARE_TO,
      GroovyTokenTypes.mEQUAL,
      GroovyTokenTypes.mBNOT,
      GroovyTokenTypes.mNOT_EQUAL,
      GroovyTokenTypes.mPLUS,
      GroovyTokenTypes.mPLUS_ASSIGN,
      GroovyTokenTypes.mINC,
      GroovyTokenTypes.mMINUS,
      GroovyTokenTypes.mMINUS_ASSIGN,
      GroovyTokenTypes.mDEC,
      GroovyTokenTypes.mSTAR,
      GroovyTokenTypes.mSTAR_ASSIGN,
      GroovyTokenTypes.mMOD,
      GroovyTokenTypes.mMOD_ASSIGN,
//          GroovyTokenTypes.mSR,
      GroovyTokenTypes.mSR_ASSIGN,
//          GroovyTokenTypes.mBSR,
      GroovyTokenTypes.mBSR_ASSIGN,
      GroovyTokenTypes.mGE,
      GroovyTokenTypes.mGT,
//          GroovyTokenTypes.mSL,
      GroovyTokenTypes.mSL_ASSIGN,
      GroovyTokenTypes.mLE,
      GroovyTokenTypes.mLT,
      GroovyTokenTypes.mBXOR,
      GroovyTokenTypes.mBXOR_ASSIGN,
      GroovyTokenTypes.mBOR,
      GroovyTokenTypes.mBOR_ASSIGN,
      GroovyTokenTypes.mLOR,
      GroovyTokenTypes.mBAND,
      GroovyTokenTypes.mBAND_ASSIGN,
      GroovyTokenTypes.mLAND,
      GroovyTokenTypes.mDOLLAR,
      GroovyTokenTypes.mRANGE_INCLUSIVE,
      GroovyTokenTypes.mRANGE_EXCLUSIVE,
      GroovyTokenTypes.mTRIPLE_DOT,
      GroovyTokenTypes.mSPREAD_DOT,
      GroovyTokenTypes.mOPTIONAL_DOT,
      GroovyTokenTypes.mMEMBER_POINTER,
      GroovyTokenTypes.mREGEX_FIND,
      GroovyTokenTypes.mREGEX_MATCH,
      GroovyTokenTypes.mSTAR_STAR,
      GroovyTokenTypes.mSTAR_STAR_ASSIGN,
      GroovyTokenTypes.mCLOSABLE_BLOCK_OP,
      GroovyTokenTypes.mAT
  );

  public static final TokenSet tKEYWORDS = TokenSet.create(
      GroovyTokenTypes.kPACKAGE,
      GroovyTokenTypes.kIMPORT,
      GroovyTokenTypes.kSTATIC,
      GroovyTokenTypes.kSTRICTFP,
      GroovyTokenTypes.kDEF,
      GroovyTokenTypes.kCLASS,
      GroovyTokenTypes.kINTERFACE,
      GroovyTokenTypes.kENUM,
      GroovyTokenTypes.kEXTENDS,
      GroovyTokenTypes.kSUPER,
      GroovyTokenTypes.kVOID,
      GroovyTokenTypes.kANY,
      GroovyTokenTypes.kBOOLEAN,
      GroovyTokenTypes.kBYTE,
      GroovyTokenTypes.kCHAR,
      GroovyTokenTypes.kSHORT,
      GroovyTokenTypes.kINT,
      GroovyTokenTypes.kFLOAT,
      GroovyTokenTypes.kLONG,
      GroovyTokenTypes.kDOUBLE,
      GroovyTokenTypes.kAS,
      GroovyTokenTypes.kPRIVATE,
      GroovyTokenTypes.kPUBLIC,
      GroovyTokenTypes.kPROTECTED,
      GroovyTokenTypes.kABSTRACT,
      GroovyTokenTypes.kTRANSIENT,
      GroovyTokenTypes.kNATIVE,
      GroovyTokenTypes.kSYNCHRONIZED,
      GroovyTokenTypes.kVOLATILE,
      GroovyTokenTypes.kDEFAULT,
      GroovyTokenTypes.kTHROWS,
      GroovyTokenTypes.kIMPLEMENTS,
      GroovyTokenTypes.kTHIS,
      GroovyTokenTypes.kIF,
      GroovyTokenTypes.kELSE,
      GroovyTokenTypes.kWHILE,
      GroovyTokenTypes.kWITH,
      GroovyTokenTypes.kSWITCH,
      GroovyTokenTypes.kFOR,
      GroovyTokenTypes.kIN,
      GroovyTokenTypes.kRETURN,
      GroovyTokenTypes.kBREAK,
      GroovyTokenTypes.kCONTINUE,
      GroovyTokenTypes.kTHROW,
      GroovyTokenTypes.kASSERT,
      GroovyTokenTypes.kCASE,
      GroovyTokenTypes.kTRY,
      GroovyTokenTypes.kFINALLY,
      GroovyTokenTypes.kFINAL,
      GroovyTokenTypes.kCATCH,
      GroovyTokenTypes.kINSTANCEOF,
      GroovyTokenTypes.kNEW,
      GroovyTokenTypes.kTRUE,
      GroovyTokenTypes.kFALSE,
      GroovyTokenTypes.kNULL
  );

  static {
    fillMap(ATTRIBUTES, tCOMMENTS, DefaultHighlighter.LINE_COMMENT);
    fillMap(ATTRIBUTES, tBAD_CHARACTERS, DefaultHighlighter.BAD_CHARACTER);
    fillMap(ATTRIBUTES, tWRONG_STRING, DefaultHighlighter.WRONG_STRING);
    fillMap(ATTRIBUTES, tKEYWORDS, DefaultHighlighter.KEYWORD);
    fillMap(ATTRIBUTES, tNUMBERS, DefaultHighlighter.NUMBER);
    fillMap(ATTRIBUTES, tSTRINGS, DefaultHighlighter.STRING);
    fillMap(ATTRIBUTES, tREGEXP, DefaultHighlighter.REGEXP);
    fillMap(ATTRIBUTES, tWRONG_REGEX, DefaultHighlighter.REGEXP);
    fillMap(ATTRIBUTES, tBRACES, DefaultHighlighter.BRACES);
  }

  @NotNull
  public Lexer getHighlightingLexer() {
    return new GroovyFlexLexer();
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(ATTRIBUTES.get(tokenType));
  }
}
