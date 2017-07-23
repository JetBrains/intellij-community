/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.util.containers.ContainerUtil;

import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.GROOVY_DOC_TOKENS;
import static org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes.GROOVY_DOC_COMMENT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;

public interface TokenSets {

  TokenSet COMMENTS_TOKEN_SET = TokenSet.create(
    mSH_COMMENT,
    mSL_COMMENT,
    mML_COMMENT,
    GROOVY_DOC_COMMENT
  );

  TokenSet ALL_COMMENT_TOKENS = TokenSet.orSet(COMMENTS_TOKEN_SET, GROOVY_DOC_TOKENS);

  TokenSet SEPARATORS = TokenSet.create(mNLS, mSEMI);

  TokenSet WHITE_SPACE_TOKEN_SET = TokenSet.create(TokenType.WHITE_SPACE);

  TokenSet NUMBERS = TokenSet.create(
    mNUM_INT,
    mNUM_BIG_DECIMAL,
    mNUM_BIG_INT,
    mNUM_DOUBLE,
    mNUM_FLOAT,
    mNUM_LONG
  );

  TokenSet CONSTANTS = TokenSet.create(
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

  TokenSet BUILT_IN_TYPES = TokenSet.create(
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

  TokenSet PROPERTY_NAMES = TokenSet.create(
    mIDENT,
    mSTRING_LITERAL,
    mGSTRING_LITERAL,
    mREGEX_LITERAL,
    mDOLLAR_SLASH_REGEX_LITERAL
  );

  TokenSet KEYWORDS = TokenSet.create(
    kABSTRACT, kAS, kASSERT,
    kBOOLEAN, kBREAK, kBYTE,
    kCASE, kCATCH, kCHAR, kCLASS, kCONTINUE,
    kDEF, kDEFAULT, kDO, kDOUBLE,
    kELSE, kEXTENDS, kENUM,
    kFALSE, kFINAL, kFLOAT, kFOR, kFINALLY,
    kIF, kIMPLEMENTS, kIMPORT, kIN, kINSTANCEOF, kINT, kINTERFACE,
    kLONG,
    kNATIVE, kNEW, kNULL,
    kPACKAGE, kPRIVATE, kPROTECTED, kPUBLIC,
    kRETURN,
    kSHORT, kSTATIC, kSTRICTFP, kSUPER, kSWITCH,
    kSYNCHRONIZED,
    kTHIS, kTHROW, kTHROWS, kTRAIT, kTRANSIENT, kTRUE, kTRY,
    kVOID, kVOLATILE,
    kWHILE
  );

  TokenSet REFERENCE_NAMES = TokenSet.orSet(KEYWORDS, PROPERTY_NAMES, NUMBERS);
  TokenSet REFERENCE_NAMES_WITHOUT_NUMBERS = TokenSet.orSet(KEYWORDS, PROPERTY_NAMES);
  TokenSet REFERENCE_NAME_PREFIXES = TokenSet.orSet(NUMBERS, KEYWORDS, TokenSet.create(mIDENT,
                                                                                       mSTRING_LITERAL,
                                                                                       mGSTRING_LITERAL,
                                                                                       mGSTRING_BEGIN,
                                                                                       mREGEX_BEGIN,
                                                                                       mDOLLAR_SLASH_REGEX_BEGIN,
                                                                                       mAT));

  TokenSet VISIBILITY_MODIFIERS = TokenSet.create(kPRIVATE, kPROTECTED, kPUBLIC);

  TokenSet MODIFIERS = TokenSet.create(
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

  TokenSet STRING_LITERALS = TokenSet.create(
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

  TokenSet GSTRING_CONTENT_PARTS = TokenSet.create(GSTRING_CONTENT, GSTRING_INJECTION);

  TokenSet FOR_IN_DELIMITERS = TokenSet.create(kIN, mCOLON);

  TokenSet RELATIONS = TokenSet.create(mLT, mGT, mLE, mGE, kIN);

  TokenSet WHITE_SPACES_SET = TokenSet.create(mNLS, TokenType.WHITE_SPACE);

  TokenSet COMMENT_SET = TokenSet.create(mML_COMMENT, mSH_COMMENT, mSL_COMMENT, GROOVY_DOC_COMMENT);

  TokenSet STRING_LITERAL_SET = TokenSet.create(mSTRING_LITERAL, mGSTRING_LITERAL, mREGEX_LITERAL, mDOLLAR_SLASH_REGEX_LITERAL);

  TokenSet LEFT_BRACES = TokenSet.create(mLBRACK, mLPAREN, mLCURLY);
  TokenSet RIGHT_BRACES = TokenSet.create(mRBRACK, mRPAREN, mRCURLY);
  TokenSet BRACES = TokenSet.orSet(LEFT_BRACES, RIGHT_BRACES);

  TokenSet UNARY_OP_SET = TokenSet.create(mBNOT, mLNOT, mMINUS, mDEC, mPLUS, mINC);

  TokenSet POSTFIX_UNARY_OP_SET = TokenSet.create(mDEC, mINC);

  TokenSet BINARY_OP_SET = TokenSet.create(mBAND, mBOR, mBXOR,
                                           mDIV, mEQUAL, mGE,
                                           mGT, mLOR, mLT,
                                           mLE, mMINUS, kAS,
                                           kIN,
                                           mMOD, mPLUS, mSTAR,
                                           mSTAR_STAR, mNOT_EQUAL,
                                           mCOMPARE_TO, mLAND,
                                           kINSTANCEOF,
                                           COMPOSITE_LSHIFT_SIGN,
                                           COMPOSITE_RSHIFT_SIGN,
                                           COMPOSITE_TRIPLE_SHIFT_SIGN,
                                           mREGEX_FIND, mREGEX_MATCH,
                                           mRANGE_INCLUSIVE, mRANGE_EXCLUSIVE);

  TokenSet PARENTHESIZED_BINARY_OP_SET = TokenSet.create(mEQUAL, mNOT_EQUAL);

  TokenSet ASSOCIATIVE_BINARY_OP_SET = TokenSet.create(mBAND, mBOR, mBXOR, mLOR, mPLUS, mSTAR, mLAND);

  TokenSet BINARY_EXPRESSIONS = TokenSet.create(ADDITIVE_EXPRESSION,
                                                MULTIPLICATIVE_EXPRESSION,
                                                POWER_EXPRESSION,
                                                POWER_EXPRESSION_SIMPLE,
                                                LOGICAL_OR_EXPRESSION,
                                                LOGICAL_AND_EXPRESSION,
                                                INCLUSIVE_OR_EXPRESSION,
                                                EXCLUSIVE_OR_EXPRESSION,
                                                AND_EXPRESSION,
                                                REGEX_FIND_EXPRESSION,
                                                REGEX_MATCH_EXPRESSION,
                                                EQUALITY_EXPRESSION,
                                                RELATIONAL_EXPRESSION,
                                                SHIFT_EXPRESSION,
                                                RANGE_EXPRESSION);

  TokenSet DOTS = TokenSet.create(mSPREAD_DOT, mOPTIONAL_DOT, mMEMBER_POINTER, mDOT);

  TokenSet WHITE_SPACES_OR_COMMENTS = TokenSet.orSet(WHITE_SPACES_SET, COMMENT_SET);

  Map<IElementType, IElementType> ASSIGNMENTS_TO_OPERATORS = new ContainerUtil.ImmutableMapBuilder<IElementType, IElementType>()
    .put(mMINUS_ASSIGN, mMINUS)
    .put(mPLUS_ASSIGN, mPLUS)
    .put(mDIV_ASSIGN, mDIV)
    .put(mSTAR_ASSIGN, mSTAR)
    .put(mMOD_ASSIGN, mMOD)
    .put(mSL_ASSIGN, COMPOSITE_LSHIFT_SIGN)
    .put(mSR_ASSIGN, COMPOSITE_RSHIFT_SIGN)
    .put(mBSR_ASSIGN, COMPOSITE_TRIPLE_SHIFT_SIGN)
    .put(mBAND_ASSIGN, mBAND)
    .put(mBOR_ASSIGN, mBOR)
    .put(mBXOR_ASSIGN, mBXOR)
    .put(mSTAR_STAR_ASSIGN, mSTAR_STAR)
    .build();

  TokenSet ASSIGNMENTS = TokenSet.create(
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

  TokenSet SHIFT_SIGNS = TokenSet.create(COMPOSITE_LSHIFT_SIGN, COMPOSITE_RSHIFT_SIGN, COMPOSITE_TRIPLE_SHIFT_SIGN);

  TokenSet CODE_REFERENCE_ELEMENT_NAME_TOKENS = TokenSet.create(mIDENT, kDEF, kIN, kAS, kTRAIT);

  TokenSet BLOCK_SET = TokenSet.create(CLOSABLE_BLOCK, BLOCK_STATEMENT, CONSTRUCTOR_BODY, OPEN_BLOCK, ENUM_BODY, CLASS_BODY);

  TokenSet METHOD_DEFS = TokenSet.create(METHOD_DEFINITION, CONSTRUCTOR_DEFINITION, ANNOTATION_METHOD);

  TokenSet VARIABLES = TokenSet.create(VARIABLE, FIELD);

  TokenSet TYPE_ELEMENTS = TokenSet.create(CLASS_TYPE_ELEMENT, ARRAY_TYPE, BUILT_IN_TYPE, TYPE_ARGUMENT, DISJUNCTION_TYPE_ELEMENT);

  TokenSet TYPE_DEFINITIONS = TokenSet.create(
    CLASS_DEFINITION,
    ENUM_DEFINITION,
    INTERFACE_DEFINITION,
    ANNOTATION_DEFINITION,
    TRAIT_DEFINITION
  );

  TokenSet METHOD_IDENTIFIERS = TokenSet.create(mIDENT, mGSTRING_LITERAL, mSTRING_LITERAL);
}
