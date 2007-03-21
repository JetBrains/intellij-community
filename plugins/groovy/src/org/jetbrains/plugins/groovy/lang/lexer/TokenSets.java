package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.psi.tree.TokenSet;

/**
 * Utility classdef, tha contains various useful TokenSets
 *
 * @author Ilya Sergey
 */
public abstract class TokenSets implements GroovyTokenTypes {

  public static TokenSet COMMENTS_TOKEN_SET = TokenSet.create(
          mSL_COMMENT,
          mML_COMMENT
  );

  public static TokenSet SEPARATORS = TokenSet.create(
          mNLS,
          mSEMI
  );


  public static TokenSet WHITE_SPACE_TOKEN_SET = TokenSet.create(
          mWS
  );

  public static TokenSet SUSPICIOUS_EXPRESSION_STATEMENT_START_TOKEN_SET = TokenSet.create(
          mMINUS,
          mPLUS,
          mLBRACK,
          mLPAREN,
          mLCURLY
  );


  public static final TokenSet CONSTANTS = TokenSet.create(
          mNUM_INT,
          kTRUE,
          kFALSE,
          kNULL,
          mSTRING_LITERAL,
          mGSTRING_LITERAL
  );

  public static final TokenSet BUILT_IN_TYPE = TokenSet.create(
          kVOID,
          kBOOLEAN,
          kBYTE,
          kCHAR,
          kSHORT,
          kINT,
          kFLOAT,
          kLONG,
          kDOUBLE,
          kANY
  );

  public static TokenSet KEYWORD_PROPERTY_NAMES = TokenSet.orSet(TokenSet.create(
          kCLASS,
          kIN,
          kAS,
          kDEF,
          kIF,
          kELSE,
          kFOR,
          kWHILE,
          kSWITCH,
          kTRY,
          kCATCH,
          kFINALLY
  ), BUILT_IN_TYPE);


}
