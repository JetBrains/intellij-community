// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets;

import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.GROOVY_DOC_TOKENS;
import static org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes.GROOVY_DOC_COMMENT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kABSTRACT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kAS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kASSERT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kBOOLEAN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kBREAK;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kBYTE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kCASE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kCATCH;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kCHAR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kCLASS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kCONTINUE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kDEF;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kDEFAULT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kDO;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kDOUBLE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kELSE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kENUM;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kEXTENDS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kFALSE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kFINAL;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kFINALLY;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kFLOAT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kFOR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kIF;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kIMPLEMENTS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kIMPORT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kIN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kINSTANCEOF;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kINT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kINTERFACE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kLONG;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kNATIVE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kNEW;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kNON_SEALED;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kNOT_IN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kNOT_INSTANCEOF;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kNULL;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kPACKAGE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kPERMITS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kPRIVATE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kPROTECTED;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kPUBLIC;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kRECORD;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kRETURN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kSEALED;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kSHORT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kSTATIC;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kSTRICTFP;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kSUPER;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kSWITCH;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kSYNCHRONIZED;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kTHIS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kTHROW;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kTHROWS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kTRAIT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kTRANSIENT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kTRUE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kTRY;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kVAR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kVOID;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kVOLATILE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kWHILE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kYIELD;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBAND;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBAND_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBNOT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBOR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBOR_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBSR_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBXOR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mBXOR_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mDEC;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mDIV;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mDIV_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mDOLLAR_SLASH_REGEX_BEGIN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mDOLLAR_SLASH_REGEX_LITERAL;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mGSTRING_BEGIN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mGSTRING_CONTENT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mGSTRING_END;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mIDENT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mINC;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLBRACK;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLCURLY;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLNOT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mLPAREN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mMINUS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mMINUS_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mML_COMMENT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mMOD;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mMOD_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mNLS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mNUM_BIG_DECIMAL;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mNUM_BIG_INT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mNUM_DOUBLE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mNUM_FLOAT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mNUM_INT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mNUM_LONG;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mPLUS;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mPLUS_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mRBRACK;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mRCURLY;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mREGEX_BEGIN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mREGEX_CONTENT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mREGEX_END;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mREGEX_LITERAL;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mRPAREN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSEMI;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSH_COMMENT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSL_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSL_COMMENT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSR_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTAR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTAR_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTAR_STAR;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mSTAR_STAR_ASSIGN;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.ADDITIVE_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.AND_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.ANNOTATION_METHOD;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.ANNOTATION_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.ARRAY_TYPE;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.BLOCK_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.BUILT_IN_TYPE;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.CLASS_BODY;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.CLASS_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.CLASS_TYPE_ELEMENT;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.CLOSABLE_BLOCK;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.CLOSABLE_BLOCK_SWITCH_AWARE;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.COMPOSITE_LSHIFT_SIGN;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.COMPOSITE_RSHIFT_SIGN;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.CONSTRUCTOR;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.CONSTRUCTOR_BODY;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.DISJUNCTION_TYPE_ELEMENT;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.ENUM_BODY;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.ENUM_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.EQUALITY_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.EXCLUSIVE_OR_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.FIELD;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.GSTRING_CONTENT;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.GSTRING_INJECTION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.INCLUSIVE_OR_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.INTERFACE_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.LOGICAL_AND_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.LOGICAL_OR_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.METHOD;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.MULTIPLICATIVE_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.OPEN_BLOCK;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.OPEN_BLOCK_SWITCH_AWARE;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.POWER_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.POWER_EXPRESSION_SIMPLE;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.RANGE_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.REGEX_FIND_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.REGEX_MATCH_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.RELATIONAL_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.SHIFT_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.TRAIT_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.TYPE_ARGUMENT;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.VARIABLE;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes.RECORD_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.BLOCK_LAMBDA_BODY;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.BLOCK_LAMBDA_BODY_SWITCH_AWARE;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_PERMITS;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_RECORD;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_VAR;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_YIELD;

public interface TokenSets {

  TokenSet COMMENTS_TOKEN_SET = TokenSet.create(
    mSH_COMMENT,
    mSL_COMMENT,
    mML_COMMENT,
    GROOVY_DOC_COMMENT
  );

  TokenSet ALL_COMMENT_TOKENS = TokenSet.orSet(COMMENTS_TOKEN_SET, GROOVY_DOC_TOKENS);

  TokenSet SEPARATORS = TokenSet.create(mNLS, mSEMI);

  TokenSet NUMBERS = TokenSet.create(
    mNUM_INT,
    mNUM_BIG_DECIMAL,
    mNUM_BIG_INT,
    mNUM_DOUBLE,
    mNUM_FLOAT,
    mNUM_LONG
  );

  TokenSet CONSTANTS = TokenSet.orSet(GroovyTokenSets.STRING_LITERALS, TokenSet.create(
    mNUM_INT,
    mNUM_BIG_DECIMAL,
    mNUM_BIG_INT,
    mNUM_DOUBLE,
    mNUM_FLOAT,
    mNUM_LONG,
    kTRUE,
    kFALSE,
    kNULL,
    mREGEX_LITERAL,
    mDOLLAR_SLASH_REGEX_LITERAL
  ));

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

  TokenSet CONTEXTUAL_KEYWORDS = TokenSet.create(
    KW_VAR,
    KW_YIELD,
    KW_RECORD,
    KW_PERMITS
  );

  TokenSet PROPERTY_NAMES = TokenSet.orSet(GroovyTokenSets.STRING_LITERALS, CONTEXTUAL_KEYWORDS, TokenSet.create(
    mIDENT,
    mREGEX_LITERAL,
    mDOLLAR_SLASH_REGEX_LITERAL
  ));

  TokenSet KEYWORDS = TokenSet.create(
    kABSTRACT, kAS, kASSERT,
    kBOOLEAN, kBREAK, kBYTE,
    kCASE, kCATCH, kCHAR, kCLASS, kCONTINUE,
    kDEF, kVAR, kDEFAULT, kDO, kDOUBLE,
    kELSE, kEXTENDS, kENUM,
    kFALSE, kFINAL, kFLOAT, kFOR, kFINALLY,
    kIF, kIMPLEMENTS, kIMPORT, kIN, kINSTANCEOF, kINT, kINTERFACE,
    kLONG,
    kNATIVE, kNEW, kNON_SEALED, kNOT_IN, kNOT_INSTANCEOF, kNULL,
    kPACKAGE, kPERMITS, kPRIVATE, kPROTECTED, kPUBLIC,
    kRECORD, kRETURN,
    kSEALED, kSHORT, kSTATIC, kSTRICTFP, kSUPER, kSWITCH,
    kSYNCHRONIZED,
    kTHIS, kTHROW, kTHROWS, kTRAIT, kTRANSIENT, kTRUE, kTRY,
    kVOID, kVOLATILE,
    kWHILE,
    kYIELD
  );

  TokenSet REFERENCE_NAMES = TokenSet.orSet(KEYWORDS, PROPERTY_NAMES, NUMBERS);
  TokenSet REFERENCE_NAMES_WITHOUT_NUMBERS = TokenSet.orSet(KEYWORDS, PROPERTY_NAMES);

  TokenSet MODIFIERS = TokenSet.create(
    kABSTRACT,
    kPRIVATE,
    kPUBLIC,
    kPROTECTED,
    kSTATIC,
    kTRANSIENT,
    kFINAL,
    kDEFAULT,
    kNATIVE,
    kSYNCHRONIZED,
    kSTRICTFP,
    kVOLATILE,
    kSTRICTFP,
    kSEALED,
    kNON_SEALED,
    kDEF,
    kVAR
  );

  TokenSet STRING_LITERALS = TokenSet.orSet(GroovyTokenSets.STRING_LITERALS, TokenSet.create(
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
  ));

  TokenSet GSTRING_CONTENT_PARTS = TokenSet.create(GSTRING_CONTENT, GSTRING_INJECTION);

  TokenSet WHITE_SPACES_SET = TokenSet.create(mNLS, TokenType.WHITE_SPACE);

  TokenSet COMMENT_SET = TokenSet.create(mML_COMMENT, mSH_COMMENT, mSL_COMMENT, GROOVY_DOC_COMMENT);

  TokenSet STRING_LITERAL_SET =
    TokenSet.orSet(GroovyTokenSets.STRING_LITERALS, TokenSet.create(mREGEX_LITERAL, mDOLLAR_SLASH_REGEX_LITERAL));

  TokenSet LEFT_BRACES = TokenSet.create(mLBRACK, mLPAREN, mLCURLY);
  TokenSet RIGHT_BRACES = TokenSet.create(mRBRACK, mRPAREN, mRCURLY);
  TokenSet BRACES = TokenSet.orSet(LEFT_BRACES, RIGHT_BRACES);

  TokenSet UNARY_OP_SET = TokenSet.create(mBNOT, mLNOT, mMINUS, mDEC, mPLUS, mINC);

  TokenSet POSTFIX_UNARY_OP_SET = TokenSet.create(mDEC, mINC);

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

  TokenSet DOTS = GroovyTokenSets.DOTS;

  TokenSet WHITE_SPACES_OR_COMMENTS = TokenSet.orSet(WHITE_SPACES_SET, COMMENT_SET);

  Map<IElementType, IElementType> ASSIGNMENTS_TO_OPERATORS = Map.ofEntries(
    Map.entry(mMINUS_ASSIGN, mMINUS),
    Map.entry(mPLUS_ASSIGN, mPLUS),
    Map.entry(mDIV_ASSIGN, mDIV),
    Map.entry(mSTAR_ASSIGN, mSTAR),
    Map.entry(mMOD_ASSIGN, mMOD),
    Map.entry(mSL_ASSIGN, COMPOSITE_LSHIFT_SIGN),
    Map.entry(mSR_ASSIGN, COMPOSITE_RSHIFT_SIGN),
    Map.entry(mBSR_ASSIGN, COMPOSITE_TRIPLE_SHIFT_SIGN),
    Map.entry(mBAND_ASSIGN, mBAND),
    Map.entry(mBOR_ASSIGN, mBOR),
    Map.entry(mBXOR_ASSIGN, mBXOR),
    Map.entry(mSTAR_STAR_ASSIGN, mSTAR_STAR));

  TokenSet CODE_REFERENCE_ELEMENT_NAME_TOKENS = TokenSet.create(mIDENT, kDEF, kIN, kAS, kTRAIT, kVAR, kYIELD, kRECORD);

  TokenSet BLOCK_SET =
    TokenSet.create(CLOSABLE_BLOCK, BLOCK_STATEMENT, CONSTRUCTOR_BODY, OPEN_BLOCK, OPEN_BLOCK_SWITCH_AWARE, CLOSABLE_BLOCK_SWITCH_AWARE, ENUM_BODY, CLASS_BODY, BLOCK_LAMBDA_BODY, BLOCK_LAMBDA_BODY_SWITCH_AWARE);

  TokenSet METHOD_DEFS = TokenSet.create(METHOD, CONSTRUCTOR, ANNOTATION_METHOD);

  TokenSet VARIABLES = TokenSet.create(VARIABLE, FIELD);

  TokenSet TYPE_ELEMENTS = TokenSet.create(CLASS_TYPE_ELEMENT, ARRAY_TYPE, BUILT_IN_TYPE, TYPE_ARGUMENT, DISJUNCTION_TYPE_ELEMENT);

  TokenSet TYPE_DEFINITIONS = TokenSet.create(
    CLASS_TYPE_DEFINITION,
    RECORD_TYPE_DEFINITION,
    ENUM_TYPE_DEFINITION,
    INTERFACE_TYPE_DEFINITION,
    ANNOTATION_TYPE_DEFINITION,
    TRAIT_TYPE_DEFINITION
  );

  TokenSet METHOD_IDENTIFIERS = TokenSet.orSet(GroovyTokenSets.STRING_LITERALS, TokenSet.create(mIDENT));

  TokenSet INVALID_INSIDE_REFERENCE = TokenSet.create(mSEMI, mLCURLY, mRCURLY);
}
