/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.hash.HashMap;

import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;

/**
 * Utility classdef, tha contains various useful TokenSets
 *
 * @author ilyas
 */
public abstract class TokenSets {

  public static final TokenSet COMMENTS_TOKEN_SET = TokenSet.create(
      mSH_COMMENT,
      mSL_COMMENT,
      mML_COMMENT,
      GROOVY_DOC_COMMENT
  );
  public static final TokenSet ALL_COMMENT_TOKENS = TokenSet.orSet(COMMENTS_TOKEN_SET, GROOVY_DOC_TOKENS);

  public static final TokenSet SEPARATORS = TokenSet.create(
      mNLS,
      mSEMI
  );


  public static final TokenSet WHITE_SPACE_TOKEN_SET = TokenSet.create(
      TokenType.WHITE_SPACE
  );

  public static final TokenSet NUMBERS = TokenSet.create(
      mNUM_INT,
      mNUM_BIG_DECIMAL,
      mNUM_BIG_INT,
      mNUM_DOUBLE,
      mNUM_FLOAT,
      mNUM_LONG);


  public static final TokenSet CONSTANTS = TokenSet.create(
      mNUM_INT,
      mNUM_BIG_DECIMAL,
      mNUM_BIG_INT,
      mNUM_DOUBLE,
      mNUM_FLOAT,
      mNUM_LONG,
      kTRUE,
      kFALSE,
      kNULL,
      mSTRING_LITERAL,
      mGSTRING_LITERAL,
      mREGEX_LITERAL,
      mDOLLAR_SLASH_REGEX_LITERAL
  );

  public static final TokenSet BUILT_IN_TYPES = TokenSet.create(
      kVOID,
      kBOOLEAN,
      kBYTE,
      kCHAR,
      kSHORT,
      kINT,
      kFLOAT,
      kLONG,
      kDOUBLE
  );

  public static final TokenSet PROPERTY_NAMES =
    TokenSet.create(mIDENT, mSTRING_LITERAL, mGSTRING_LITERAL, mREGEX_LITERAL, mDOLLAR_SLASH_REGEX_LITERAL);

  public static final TokenSet KEYWORDS = TokenSet.create(kABSTRACT, kAS, kASSERT, kBOOLEAN, kBREAK, kBYTE, kCASE, kCATCH, kCHAR, kCLASS,
                                                          kCONTINUE, kDEF, kDEFAULT, kDO, kDOUBLE, kELSE, kEXTENDS, kENUM, kFALSE, kFINAL,
                                                          kFLOAT, kFOR, kFINALLY, kIF, kIMPLEMENTS, kIMPORT, kIN, kINSTANCEOF, kINT,
                                                          kINTERFACE, kLONG, kNATIVE, kNEW, kNULL, kPACKAGE, kPRIVATE, kPROTECTED, kPUBLIC,
                                                          kRETURN, kSHORT, kSTATIC, kSTRICTFP, kSUPER, kSWITCH, kSYNCHRONIZED, kTHIS,
                                                          kTHROW, kTHROWS, kTRANSIENT, kTRUE, kTRY, kVOID, kVOLATILE, kWHILE);

  public static final TokenSet REFERENCE_NAMES = TokenSet.orSet(KEYWORDS, PROPERTY_NAMES, NUMBERS);
  public static final TokenSet REFERENCE_NAMES_WITHOUT_NUMBERS = TokenSet.orSet(KEYWORDS, PROPERTY_NAMES);


  public static final TokenSet VISIBILITY_MODIFIERS = TokenSet.create(
      kPRIVATE,
      kPROTECTED,
      kPUBLIC
  );

  public static final TokenSet MODIFIERS = TokenSet.create(
      kABSTRACT,
      kPRIVATE,
      kPUBLIC,
      kPROTECTED,
      kSTATIC,
      kTRANSIENT,
      kFINAL,
      kABSTRACT,
      kNATIVE,
      kSYNCHRONIZED,
      kSTRICTFP,
      kVOLATILE,
      kSTRICTFP,
      kDEF
  );

  public static final TokenSet STRING_LITERALS = TokenSet.create(
      mSTRING_LITERAL,
      mGSTRING_LITERAL,
      mGSTRING_BEGIN,
      mGSTRING_CONTENT,
      mGSTRING_END,
      mREGEX_LITERAL,
      mREGEX_BEGIN,
      mREGEX_CONTENT,
      mREGEX_END,
      mDOLLAR_SLASH_REGEX_LITERAL,
      mDOLLAR_SLASH_REGEX_BEGIN,
      mDOLLAR_SLASH_REGEX_CONTENT,
      mDOLLAR_SLASH_REGEX_END
  );

  public static final TokenSet GSTRING_CONTENT_PARTS = TokenSet.create(GSTRING_CONTENT, GSTRING_INJECTION);

  public static final TokenSet FOR_IN_DELIMITERS = TokenSet.create(kIN, mCOLON);

  public static final TokenSet RELATIONS = TokenSet.create(
          mLT,
          mGT,
          mLE,
          mGE,
          kIN
  );
  public static final TokenSet WHITE_SPACES_SET = TokenSet.create(mNLS, TokenType.WHITE_SPACE);

  public static final TokenSet COMMENT_SET = TokenSet.create(mML_COMMENT, mSH_COMMENT, mSL_COMMENT, GROOVY_DOC_COMMENT);

  public static final TokenSet STRING_LITERAL_SET = TokenSet.create(mSTRING_LITERAL, mGSTRING_LITERAL, mREGEX_LITERAL, mDOLLAR_SLASH_REGEX_LITERAL);

  public static final TokenSet BRACES = TokenSet.create(mLBRACK, mRBRACK, mLPAREN, mRPAREN, mLCURLY, mRCURLY);

  public static final TokenSet ASSIGN_OP_SET = TokenSet.create(mASSIGN, mBAND_ASSIGN, mBOR_ASSIGN, mBSR_ASSIGN, mBXOR_ASSIGN, mDIV_ASSIGN,
                                                               mMINUS_ASSIGN, mMOD_ASSIGN, mPLUS_ASSIGN, mSL_ASSIGN, mSR_ASSIGN,
                                                               mSTAR_ASSIGN, mSTAR_STAR_ASSIGN);

  public static final TokenSet UNARY_OP_SET = TokenSet.create(mBNOT, mLNOT, mMINUS, mDEC, mPLUS, mINC);

  public static final TokenSet POSTFIX_UNARY_OP_SET = TokenSet.create(mDEC, mINC);

  public static final TokenSet BINARY_OP_SET = TokenSet.create(mBAND, mBOR, mBXOR, mDIV, mEQUAL, mGE, mGT, mLOR, mLT, mLE, mMINUS, kAS, kIN,
                                                               mMOD, mPLUS, mSTAR, mSTAR_STAR, mNOT_EQUAL, mCOMPARE_TO, mLAND, kINSTANCEOF,
                                                               COMPOSITE_LSHIFT_SIGN, COMPOSITE_RSHIFT_SIGN, COMPOSITE_TRIPLE_SHIFT_SIGN,
                                                               mREGEX_FIND, mREGEX_MATCH, mRANGE_INCLUSIVE, mRANGE_EXCLUSIVE);

  public static final TokenSet BINARY_EXPRESSIONS = TokenSet.create(ADDITIVE_EXPRESSION, MULTIPLICATIVE_EXPRESSION, POWER_EXPRESSION,
                                                                    POWER_EXPRESSION_SIMPLE, LOGICAL_OR_EXPRESSION, LOGICAL_AND_EXPRESSION,
                                                                    INCLUSIVE_OR_EXPRESSION, EXCLUSIVE_OR_EXPRESSION, AND_EXPRESSION,
                                                                    REGEX_FIND_EXPRESSION, REGEX_MATCH_EXPRESSION, EQUALITY_EXPRESSION,
                                                                    RELATIONAL_EXPRESSION, SHIFT_EXPRESSION, RANGE_EXPRESSION);

  public static final TokenSet DOTS = TokenSet.create(mSPREAD_DOT, mOPTIONAL_DOT, mMEMBER_POINTER, mDOT);

  public static final TokenSet WHITE_SPACES_OR_COMMENTS = TokenSet.orSet(WHITE_SPACES_SET, COMMENT_SET);

  public static final Map<IElementType, IElementType> ASSIGNMENTS_TO_OPERATORS = new HashMap<IElementType, IElementType>();
  static {
    ASSIGNMENTS_TO_OPERATORS.put(mMINUS_ASSIGN, mMINUS);
    ASSIGNMENTS_TO_OPERATORS.put(mPLUS_ASSIGN, mPLUS);
    ASSIGNMENTS_TO_OPERATORS.put(mDIV_ASSIGN, mDIV);
    ASSIGNMENTS_TO_OPERATORS.put(mSTAR_ASSIGN, mSTAR);
    ASSIGNMENTS_TO_OPERATORS.put(mMOD_ASSIGN, mMOD);
    ASSIGNMENTS_TO_OPERATORS.put(mSL_ASSIGN, COMPOSITE_LSHIFT_SIGN);
    ASSIGNMENTS_TO_OPERATORS.put(mSR_ASSIGN, COMPOSITE_RSHIFT_SIGN);
    ASSIGNMENTS_TO_OPERATORS.put(mBSR_ASSIGN, COMPOSITE_TRIPLE_SHIFT_SIGN);
    ASSIGNMENTS_TO_OPERATORS.put(mBAND_ASSIGN, mBAND);
    ASSIGNMENTS_TO_OPERATORS.put(mBOR_ASSIGN, mBOR);
    ASSIGNMENTS_TO_OPERATORS.put(mBXOR_ASSIGN, mBXOR);
    ASSIGNMENTS_TO_OPERATORS.put(mSTAR_STAR_ASSIGN, mSTAR_STAR);
  }

  public static final TokenSet ASSIGNMENTS = TokenSet.create(
          mASSIGN,
          mPLUS_ASSIGN,
          mMINUS_ASSIGN,
          mSTAR_ASSIGN,
          mDIV_ASSIGN,
          mMOD_ASSIGN,
          mSL_ASSIGN,
          mSR_ASSIGN,
          mBSR_ASSIGN,
          mBAND_ASSIGN,
          mBOR_ASSIGN,
          mBXOR_ASSIGN,
          mSTAR_STAR_ASSIGN
  );

  public static final TokenSet SHIFT_SIGNS = TokenSet.create(COMPOSITE_LSHIFT_SIGN, COMPOSITE_RSHIFT_SIGN, COMPOSITE_TRIPLE_SHIFT_SIGN);
  public static final TokenSet CODE_REFERENCE_ELEMENT_NAME_TOKENS = TokenSet.create(mIDENT, kDEF,  kIN, kAS);

  public static final TokenSet BLOCK_SET = TokenSet.create(CLOSABLE_BLOCK, BLOCK_STATEMENT, CONSTRUCTOR_BODY, OPEN_BLOCK, ENUM_BODY, CLASS_BODY);
  public static final TokenSet METHOD_DEFS = TokenSet.create(METHOD_DEFINITION, CONSTRUCTOR_DEFINITION, ANNOTATION_METHOD);
  public static final TokenSet VARIABLES = TokenSet.create(VARIABLE, FIELD);
  public static final TokenSet TYPE_ELEMENTS = TokenSet.create(CLASS_TYPE_ELEMENT, ARRAY_TYPE, BUILT_IN_TYPE, TYPE_ARGUMENT, DISJUNCTION_TYPE_ELEMENT);


  public static final TokenSet TYPE_DEFINITIONS = TokenSet.create(CLASS_DEFINITION, ENUM_DEFINITION, INTERFACE_DEFINITION, ANNOTATION_DEFINITION);
}
