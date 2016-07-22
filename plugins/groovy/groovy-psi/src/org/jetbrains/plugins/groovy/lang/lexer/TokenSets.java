/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;

import java.util.Map;

/**
 * Utility classdef, tha contains various useful TokenSets
 *
 * @author ilyas
 */
public abstract class TokenSets {

  public static final TokenSet COMMENTS_TOKEN_SET = TokenSet.create(
    GroovyTokenTypes.mSH_COMMENT,
    GroovyTokenTypes.mSL_COMMENT,
    GroovyTokenTypes.mML_COMMENT,
    GroovyDocElementTypes.GROOVY_DOC_COMMENT
  );
  public static final TokenSet ALL_COMMENT_TOKENS = TokenSet.orSet(COMMENTS_TOKEN_SET, GroovyDocTokenTypes.GROOVY_DOC_TOKENS);

  public static final TokenSet SEPARATORS = TokenSet.create(
    GroovyTokenTypes.mNLS,
    GroovyTokenTypes.mSEMI
  );


  public static final TokenSet WHITE_SPACE_TOKEN_SET = TokenSet.create(
      TokenType.WHITE_SPACE
  );

  public static final TokenSet NUMBERS = TokenSet.create(
    GroovyTokenTypes.mNUM_INT,
    GroovyTokenTypes.mNUM_BIG_DECIMAL,
    GroovyTokenTypes.mNUM_BIG_INT,
    GroovyTokenTypes.mNUM_DOUBLE,
    GroovyTokenTypes.mNUM_FLOAT,
    GroovyTokenTypes.mNUM_LONG);


  public static final TokenSet CONSTANTS = TokenSet.create(
    GroovyTokenTypes.mNUM_INT,
    GroovyTokenTypes.mNUM_BIG_DECIMAL,
    GroovyTokenTypes.mNUM_BIG_INT,
    GroovyTokenTypes.mNUM_DOUBLE,
    GroovyTokenTypes.mNUM_FLOAT,
    GroovyTokenTypes.mNUM_LONG,
    GroovyTokenTypes.kTRUE,
    GroovyTokenTypes.kFALSE,
    GroovyTokenTypes.kNULL,
    GroovyTokenTypes.mSTRING_LITERAL,
    GroovyTokenTypes.mGSTRING_LITERAL,
    GroovyTokenTypes.mREGEX_LITERAL,
    GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL
  );

  public static final TokenSet BUILT_IN_TYPES = TokenSet.create(
    GroovyTokenTypes.kVOID,
    GroovyTokenTypes.kBOOLEAN,
    GroovyTokenTypes.kBYTE,
    GroovyTokenTypes.kCHAR,
    GroovyTokenTypes.kSHORT,
    GroovyTokenTypes.kINT,
    GroovyTokenTypes.kFLOAT,
    GroovyTokenTypes.kLONG,
    GroovyTokenTypes.kDOUBLE
  );

  public static final TokenSet PROPERTY_NAMES =
    TokenSet.create(GroovyTokenTypes.mIDENT, GroovyTokenTypes.mSTRING_LITERAL, GroovyTokenTypes.mGSTRING_LITERAL,
                    GroovyTokenTypes.mREGEX_LITERAL, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL);

  public static final TokenSet KEYWORDS = TokenSet.create(GroovyTokenTypes.kABSTRACT, GroovyTokenTypes.kAS, GroovyTokenTypes.kASSERT,
                                                          GroovyTokenTypes.kBOOLEAN, GroovyTokenTypes.kBREAK, GroovyTokenTypes.kBYTE,
                                                          GroovyTokenTypes.kCASE, GroovyTokenTypes.kCATCH, GroovyTokenTypes.kCHAR,
                                                          GroovyTokenTypes.kCLASS,
                                                          GroovyTokenTypes.kCONTINUE, GroovyTokenTypes.kDEF, GroovyTokenTypes.kDEFAULT,
                                                          GroovyTokenTypes.kDO, GroovyTokenTypes.kDOUBLE, GroovyTokenTypes.kELSE,
                                                          GroovyTokenTypes.kEXTENDS, GroovyTokenTypes.kENUM, GroovyTokenTypes.kFALSE,
                                                          GroovyTokenTypes.kFINAL,
                                                          GroovyTokenTypes.kFLOAT, GroovyTokenTypes.kFOR, GroovyTokenTypes.kFINALLY,
                                                          GroovyTokenTypes.kIF, GroovyTokenTypes.kIMPLEMENTS, GroovyTokenTypes.kIMPORT,
                                                          GroovyTokenTypes.kIN, GroovyTokenTypes.kINSTANCEOF, GroovyTokenTypes.kINT,
                                                          GroovyTokenTypes.kINTERFACE, GroovyTokenTypes.kLONG, GroovyTokenTypes.kNATIVE,
                                                          GroovyTokenTypes.kNEW, GroovyTokenTypes.kNULL, GroovyTokenTypes.kPACKAGE,
                                                          GroovyTokenTypes.kPRIVATE, GroovyTokenTypes.kPROTECTED, GroovyTokenTypes.kPUBLIC,
                                                          GroovyTokenTypes.kRETURN, GroovyTokenTypes.kSHORT, GroovyTokenTypes.kSTATIC,
                                                          GroovyTokenTypes.kSTRICTFP, GroovyTokenTypes.kSUPER, GroovyTokenTypes.kSWITCH,
                                                          GroovyTokenTypes.kSYNCHRONIZED, GroovyTokenTypes.kTHIS,
                                                          GroovyTokenTypes.kTHROW, GroovyTokenTypes.kTHROWS, GroovyTokenTypes.kTRAIT,
                                                          GroovyTokenTypes.kTRANSIENT, GroovyTokenTypes.kTRUE, GroovyTokenTypes.kTRY,
                                                          GroovyTokenTypes.kVOID, GroovyTokenTypes.kVOLATILE, GroovyTokenTypes.kWHILE);

  public static final TokenSet REFERENCE_NAMES = TokenSet.orSet(KEYWORDS, PROPERTY_NAMES, NUMBERS);
  public static final TokenSet REFERENCE_NAMES_WITHOUT_NUMBERS = TokenSet.orSet(KEYWORDS, PROPERTY_NAMES);

  public static final TokenSet REFERENCE_NAME_PREFIXES = TokenSet.orSet(NUMBERS, KEYWORDS, TokenSet.create(GroovyTokenTypes.mIDENT,
                                                                                                           GroovyTokenTypes.mSTRING_LITERAL,
                                                                                                           GroovyTokenTypes.mGSTRING_LITERAL,
                                                                                                           GroovyTokenTypes.mGSTRING_BEGIN,
                                                                                                           GroovyTokenTypes.mREGEX_BEGIN,
                                                                                                           GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN,
                                                                                                           GroovyTokenTypes.mAT));


  public static final TokenSet VISIBILITY_MODIFIERS = TokenSet.create(
    GroovyTokenTypes.kPRIVATE,
    GroovyTokenTypes.kPROTECTED,
    GroovyTokenTypes.kPUBLIC
  );

  public static final TokenSet MODIFIERS = TokenSet.create(
    GroovyTokenTypes.kABSTRACT,
    GroovyTokenTypes.kPRIVATE,
    GroovyTokenTypes.kPUBLIC,
    GroovyTokenTypes.kPROTECTED,
    GroovyTokenTypes.kSTATIC,
    GroovyTokenTypes.kTRANSIENT,
    GroovyTokenTypes.kFINAL,
    GroovyTokenTypes.kABSTRACT,
    GroovyTokenTypes.kNATIVE,
    GroovyTokenTypes.kSYNCHRONIZED,
    GroovyTokenTypes.kSTRICTFP,
    GroovyTokenTypes.kVOLATILE,
    GroovyTokenTypes.kSTRICTFP,
    GroovyTokenTypes.kDEF
  );

  public static final TokenSet STRING_LITERALS = TokenSet.create(
    GroovyTokenTypes.mSTRING_LITERAL,
    GroovyTokenTypes.mGSTRING_LITERAL,
    GroovyTokenTypes.mGSTRING_BEGIN,
    GroovyTokenTypes.mGSTRING_CONTENT,
    GroovyTokenTypes.mGSTRING_END,
    GroovyTokenTypes.mREGEX_LITERAL,
    GroovyTokenTypes.mREGEX_BEGIN,
    GroovyTokenTypes.mREGEX_CONTENT,
    GroovyTokenTypes.mREGEX_END,
    GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL,
    GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN,
    GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT,
    GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END
  );

  public static final TokenSet GSTRING_CONTENT_PARTS = TokenSet.create(GroovyElementTypes.GSTRING_CONTENT,
                                                                       GroovyElementTypes.GSTRING_INJECTION);

  public static final TokenSet FOR_IN_DELIMITERS = TokenSet.create(GroovyTokenTypes.kIN, GroovyTokenTypes.mCOLON);

  public static final TokenSet RELATIONS = TokenSet.create(
    GroovyTokenTypes.mLT,
    GroovyTokenTypes.mGT,
    GroovyTokenTypes.mLE,
    GroovyTokenTypes.mGE,
    GroovyTokenTypes.kIN
  );
  public static final TokenSet WHITE_SPACES_SET = TokenSet.create(GroovyTokenTypes.mNLS, TokenType.WHITE_SPACE);

  public static final TokenSet COMMENT_SET = TokenSet.create(GroovyTokenTypes.mML_COMMENT, GroovyTokenTypes.mSH_COMMENT,
                                                             GroovyTokenTypes.mSL_COMMENT, GroovyDocElementTypes.GROOVY_DOC_COMMENT);

  public static final TokenSet STRING_LITERAL_SET = TokenSet.create(GroovyTokenTypes.mSTRING_LITERAL, GroovyTokenTypes.mGSTRING_LITERAL,
                                                                    GroovyTokenTypes.mREGEX_LITERAL,
                                                                    GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL);

  public static final TokenSet BRACES = TokenSet.create(GroovyTokenTypes.mLBRACK, GroovyTokenTypes.mRBRACK, GroovyTokenTypes.mLPAREN,
                                                        GroovyTokenTypes.mRPAREN, GroovyTokenTypes.mLCURLY, GroovyTokenTypes.mRCURLY);

  public static final TokenSet ASSIGN_OP_SET = TokenSet.create(GroovyTokenTypes.mASSIGN, GroovyTokenTypes.mBAND_ASSIGN,
                                                               GroovyTokenTypes.mBOR_ASSIGN, GroovyTokenTypes.mBSR_ASSIGN,
                                                               GroovyTokenTypes.mBXOR_ASSIGN, GroovyTokenTypes.mDIV_ASSIGN,
                                                               GroovyTokenTypes.mMINUS_ASSIGN, GroovyTokenTypes.mMOD_ASSIGN,
                                                               GroovyTokenTypes.mPLUS_ASSIGN, GroovyTokenTypes.mSL_ASSIGN,
                                                               GroovyTokenTypes.mSR_ASSIGN,
                                                               GroovyTokenTypes.mSTAR_ASSIGN, GroovyTokenTypes.mSTAR_STAR_ASSIGN);

  public static final TokenSet UNARY_OP_SET = TokenSet.create(GroovyTokenTypes.mBNOT, GroovyTokenTypes.mLNOT, GroovyTokenTypes.mMINUS,
                                                              GroovyTokenTypes.mDEC, GroovyTokenTypes.mPLUS, GroovyTokenTypes.mINC);

  public static final TokenSet POSTFIX_UNARY_OP_SET = TokenSet.create(GroovyTokenTypes.mDEC, GroovyTokenTypes.mINC);

  public static final TokenSet BINARY_OP_SET = TokenSet.create(GroovyTokenTypes.mBAND, GroovyTokenTypes.mBOR, GroovyTokenTypes.mBXOR,
                                                               GroovyTokenTypes.mDIV, GroovyTokenTypes.mEQUAL, GroovyTokenTypes.mGE,
                                                               GroovyTokenTypes.mGT, GroovyTokenTypes.mLOR, GroovyTokenTypes.mLT,
                                                               GroovyTokenTypes.mLE, GroovyTokenTypes.mMINUS, GroovyTokenTypes.kAS,
                                                               GroovyTokenTypes.kIN,
                                                               GroovyTokenTypes.mMOD, GroovyTokenTypes.mPLUS, GroovyTokenTypes.mSTAR,
                                                               GroovyTokenTypes.mSTAR_STAR, GroovyTokenTypes.mNOT_EQUAL,
                                                               GroovyTokenTypes.mCOMPARE_TO, GroovyTokenTypes.mLAND,
                                                               GroovyTokenTypes.kINSTANCEOF,
                                                               GroovyElementTypes.COMPOSITE_LSHIFT_SIGN,
                                                               GroovyElementTypes.COMPOSITE_RSHIFT_SIGN,
                                                               GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN,
                                                               GroovyTokenTypes.mREGEX_FIND, GroovyTokenTypes.mREGEX_MATCH,
                                                               GroovyTokenTypes.mRANGE_INCLUSIVE, GroovyTokenTypes.mRANGE_EXCLUSIVE);

  public static final TokenSet ASSOCIATIVE_BINARY_OP_SET = TokenSet.create(GroovyTokenTypes.mBAND, GroovyTokenTypes.mBOR,
                                                                           GroovyTokenTypes.mBXOR, GroovyTokenTypes.mEQUAL,
                                                                           GroovyTokenTypes.mLOR, GroovyTokenTypes.mPLUS,
                                                                           GroovyTokenTypes.mSTAR, GroovyTokenTypes.mNOT_EQUAL,
                                                                           GroovyTokenTypes.mLAND);


  public static final TokenSet BINARY_EXPRESSIONS = TokenSet.create(GroovyElementTypes.ADDITIVE_EXPRESSION,
                                                                    GroovyElementTypes.MULTIPLICATIVE_EXPRESSION,
                                                                    GroovyElementTypes.POWER_EXPRESSION,
                                                                    GroovyElementTypes.POWER_EXPRESSION_SIMPLE,
                                                                    GroovyElementTypes.LOGICAL_OR_EXPRESSION,
                                                                    GroovyElementTypes.LOGICAL_AND_EXPRESSION,
                                                                    GroovyElementTypes.INCLUSIVE_OR_EXPRESSION,
                                                                    GroovyElementTypes.EXCLUSIVE_OR_EXPRESSION,
                                                                    GroovyElementTypes.AND_EXPRESSION,
                                                                    GroovyElementTypes.REGEX_FIND_EXPRESSION,
                                                                    GroovyElementTypes.REGEX_MATCH_EXPRESSION,
                                                                    GroovyElementTypes.EQUALITY_EXPRESSION,
                                                                    GroovyElementTypes.RELATIONAL_EXPRESSION,
                                                                    GroovyElementTypes.SHIFT_EXPRESSION,
                                                                    GroovyElementTypes.RANGE_EXPRESSION);

  public static final TokenSet DOTS = TokenSet.create(GroovyTokenTypes.mSPREAD_DOT, GroovyTokenTypes.mOPTIONAL_DOT,
                                                      GroovyTokenTypes.mMEMBER_POINTER, GroovyTokenTypes.mDOT);

  public static final TokenSet WHITE_SPACES_OR_COMMENTS = TokenSet.orSet(WHITE_SPACES_SET, COMMENT_SET);

  public static final Map<IElementType, IElementType> ASSIGNMENTS_TO_OPERATORS = new HashMap<>();
  static {
    ASSIGNMENTS_TO_OPERATORS.put(GroovyTokenTypes.mMINUS_ASSIGN, GroovyTokenTypes.mMINUS);
    ASSIGNMENTS_TO_OPERATORS.put(GroovyTokenTypes.mPLUS_ASSIGN, GroovyTokenTypes.mPLUS);
    ASSIGNMENTS_TO_OPERATORS.put(GroovyTokenTypes.mDIV_ASSIGN, GroovyTokenTypes.mDIV);
    ASSIGNMENTS_TO_OPERATORS.put(GroovyTokenTypes.mSTAR_ASSIGN, GroovyTokenTypes.mSTAR);
    ASSIGNMENTS_TO_OPERATORS.put(GroovyTokenTypes.mMOD_ASSIGN, GroovyTokenTypes.mMOD);
    ASSIGNMENTS_TO_OPERATORS.put(GroovyTokenTypes.mSL_ASSIGN, GroovyElementTypes.COMPOSITE_LSHIFT_SIGN);
    ASSIGNMENTS_TO_OPERATORS.put(GroovyTokenTypes.mSR_ASSIGN, GroovyElementTypes.COMPOSITE_RSHIFT_SIGN);
    ASSIGNMENTS_TO_OPERATORS.put(GroovyTokenTypes.mBSR_ASSIGN, GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN);
    ASSIGNMENTS_TO_OPERATORS.put(GroovyTokenTypes.mBAND_ASSIGN, GroovyTokenTypes.mBAND);
    ASSIGNMENTS_TO_OPERATORS.put(GroovyTokenTypes.mBOR_ASSIGN, GroovyTokenTypes.mBOR);
    ASSIGNMENTS_TO_OPERATORS.put(GroovyTokenTypes.mBXOR_ASSIGN, GroovyTokenTypes.mBXOR);
    ASSIGNMENTS_TO_OPERATORS.put(GroovyTokenTypes.mSTAR_STAR_ASSIGN, GroovyTokenTypes.mSTAR_STAR);
  }

  public static final TokenSet ASSIGNMENTS = TokenSet.create(
    GroovyTokenTypes.mASSIGN,
    GroovyTokenTypes.mPLUS_ASSIGN,
    GroovyTokenTypes.mMINUS_ASSIGN,
    GroovyTokenTypes.mSTAR_ASSIGN,
    GroovyTokenTypes.mDIV_ASSIGN,
    GroovyTokenTypes.mMOD_ASSIGN,
    GroovyTokenTypes.mSL_ASSIGN,
    GroovyTokenTypes.mSR_ASSIGN,
    GroovyTokenTypes.mBSR_ASSIGN,
    GroovyTokenTypes.mBAND_ASSIGN,
    GroovyTokenTypes.mBOR_ASSIGN,
    GroovyTokenTypes.mBXOR_ASSIGN,
    GroovyTokenTypes.mSTAR_STAR_ASSIGN
  );

  public static final TokenSet SHIFT_SIGNS = TokenSet.create(GroovyElementTypes.COMPOSITE_LSHIFT_SIGN,
                                                             GroovyElementTypes.COMPOSITE_RSHIFT_SIGN,
                                                             GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN);
  public static final TokenSet CODE_REFERENCE_ELEMENT_NAME_TOKENS = TokenSet.create(GroovyTokenTypes.mIDENT, GroovyTokenTypes.kDEF,
                                                                                    GroovyTokenTypes.kIN, GroovyTokenTypes.kAS);

  public static final TokenSet BLOCK_SET = TokenSet.create(GroovyElementTypes.CLOSABLE_BLOCK, GroovyElementTypes.BLOCK_STATEMENT,
                                                           GroovyElementTypes.CONSTRUCTOR_BODY, GroovyElementTypes.OPEN_BLOCK,
                                                           GroovyElementTypes.ENUM_BODY, GroovyElementTypes.CLASS_BODY);
  public static final TokenSet METHOD_DEFS = TokenSet.create(GroovyElementTypes.METHOD_DEFINITION,
                                                             GroovyElementTypes.CONSTRUCTOR_DEFINITION,
                                                             GroovyElementTypes.ANNOTATION_METHOD);
  public static final TokenSet VARIABLES = TokenSet.create(GroovyElementTypes.VARIABLE, GroovyElementTypes.FIELD);
  public static final TokenSet TYPE_ELEMENTS = TokenSet.create(GroovyElementTypes.CLASS_TYPE_ELEMENT, GroovyElementTypes.ARRAY_TYPE,
                                                               GroovyElementTypes.BUILT_IN_TYPE, GroovyElementTypes.TYPE_ARGUMENT,
                                                               GroovyElementTypes.DISJUNCTION_TYPE_ELEMENT);


  public static final TokenSet TYPE_DEFINITIONS = TokenSet.create(GroovyElementTypes.CLASS_DEFINITION, 
                                                                  GroovyElementTypes.ENUM_DEFINITION,
                                                                  GroovyElementTypes.INTERFACE_DEFINITION,
                                                                  GroovyElementTypes.ANNOTATION_DEFINITION,
                                                                  GroovyElementTypes.TRAIT_DEFINITION);
}
