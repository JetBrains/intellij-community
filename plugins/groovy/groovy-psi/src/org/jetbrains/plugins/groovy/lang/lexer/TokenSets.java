// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.lang.psi.GroovyTokenSets;

import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes.GROOVY_DOC_TOKENS;
import static org.jetbrains.plugins.groovy.lang.groovydoc.parser.GroovyDocElementTypes.GROOVY_DOC_COMMENT;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.ADDITIVE_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.ANNOTATION_METHOD;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.ANNOTATION_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.BLOCK_STATEMENT;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.CLASS_BODY;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.CLASS_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.CLASS_TYPE_ELEMENT;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.CONSTRUCTOR;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.DISJUNCTION_TYPE_ELEMENT;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.ENUM_BODY;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.ENUM_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.EQUALITY_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.FIELD;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.GSTRING_CONTENT;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.INTERFACE_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.METHOD;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.MULTIPLICATIVE_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.OPEN_BLOCK;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.OPEN_BLOCK_SWITCH_AWARE;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.POWER_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.RANGE_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.REGEX_FIND_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.REGEX_MATCH_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.RELATIONAL_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.SHIFT_EXPRESSION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.TRAIT_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.VARIABLE;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes.RECORD_TYPE_DEFINITION;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;

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
