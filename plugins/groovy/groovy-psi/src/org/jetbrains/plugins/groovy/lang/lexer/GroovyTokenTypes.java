// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.psi.tree.IElementType;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;

/**
 * Interface that contains all tokens returned by GroovyLexer
 *
 * @author ilyas
 */
public interface GroovyTokenTypes {

  /**
   * Wrong token. Use for debug needs
   */
  IElementType mWRONG = T_WRONG;

  /* **************************************************************************************************
 *  Whitespaces & NewLines
 * ****************************************************************************************************/

  IElementType mNLS = NL;

  /* **************************************************************************************************
 *  Comments
 * ****************************************************************************************************/

  IElementType mSH_COMMENT = SH_COMMENT;
  IElementType mSL_COMMENT = SL_COMMENT;
  IElementType mML_COMMENT = ML_COMMENT;

  /* **************************************************************************************************
 *  Identifiers
 * ****************************************************************************************************/

  IElementType mIDENT = IDENTIFIER;

  /* **************************************************************************************************
 *  Integers & floats
 * ****************************************************************************************************/

  IElementType mNUM_INT = NUM_INT;
  IElementType mNUM_LONG = NUM_LONG;
  IElementType mNUM_BIG_INT = NUM_BIG_INT;
  IElementType mNUM_BIG_DECIMAL = NUM_BIG_DECIMAL;
  IElementType mNUM_FLOAT = NUM_FLOAT;
  IElementType mNUM_DOUBLE = NUM_DOUBLE;

  /* **************************************************************************************************
 *  Strings & regular expressions
 * ****************************************************************************************************/

  IElementType mSTRING_LITERAL = STR_SQ;
  IElementType mGSTRING_LITERAL = STR_DQ;

  IElementType mGSTRING_BEGIN = GSTRING_BEGIN;
  IElementType mGSTRING_CONTENT = GSTRING_CONTENT;
  IElementType mGSTRING_END = GSTRING_END;

  IElementType mREGEX_BEGIN = SLASHY_BEGIN;
  IElementType mREGEX_CONTENT = SLASHY_CONTENT;
  IElementType mREGEX_END = SLASHY_END;
  IElementType mREGEX_LITERAL = SLASHY_LITERAL;

  IElementType mDOLLAR_SLASH_REGEX_BEGIN = DOLLAR_SLASHY_BEGIN;
  IElementType mDOLLAR_SLASH_REGEX_CONTENT = DOLLAR_SLASHY_CONTENT;
  IElementType mDOLLAR_SLASH_REGEX_END = DOLLAR_SLASHY_END;
  IElementType mDOLLAR_SLASH_REGEX_LITERAL = DOLLAR_SLASHY_LITERAL;


  /* **************************************************************************************************
 *  Common tokens: operators, braces etc.
 * ****************************************************************************************************/

  IElementType mQUESTION = T_Q;
  IElementType mDIV = T_DIV;
  IElementType mDIV_ASSIGN = T_DIV_ASSIGN;
  IElementType mLPAREN = T_LPAREN;
  IElementType mRPAREN = T_RPAREN;
  IElementType mLBRACK = T_LBRACK;
  IElementType mRBRACK = T_RBRACK;
  IElementType mLCURLY = T_LBRACE;
  IElementType mRCURLY = T_RBRACE;
  IElementType mCOLON = T_COLON;
  IElementType mCOMMA = T_COMMA;
  IElementType mDOT = T_DOT;
  IElementType mASSIGN = T_ASSIGN;
  IElementType mCOMPARE_TO = T_COMPARE;
  IElementType mEQUAL = T_EQ;
  IElementType mLNOT = T_NOT;
  IElementType mELVIS = T_ELVIS;
  IElementType mBNOT = T_BNOT;
  IElementType mNOT_EQUAL = T_NEQ;
  IElementType mPLUS = T_PLUS;
  IElementType mPLUS_ASSIGN = T_PLUS_ASSIGN;
  IElementType mINC = T_INC;
  IElementType mMINUS = T_MINUS;
  IElementType mMINUS_ASSIGN = T_MINUS_ASSIGN;
  IElementType mDEC = T_DEC;
  IElementType mSTAR = T_STAR;
  IElementType mSTAR_ASSIGN = T_STAR_ASSIGN;
  IElementType mMOD = T_REM;
  IElementType mMOD_ASSIGN = T_REM_ASSIGN;
  IElementType mBSR_ASSIGN = T_RSHU_ASSIGN;
  IElementType mSR_ASSIGN = T_RSH_ASSIGN;
  IElementType mGE = T_GE;
  IElementType mGT = T_GT;
  IElementType mSL_ASSIGN = T_LSH_ASSIGN;
  IElementType mLE = T_LE;
  IElementType mLT = T_LT;
  IElementType mBXOR = T_XOR;
  IElementType mBXOR_ASSIGN = T_XOR_ASSIGN;
  IElementType mBOR = T_BOR;
  IElementType mBOR_ASSIGN = T_BOR_ASSIGN;
  IElementType mLOR = T_LOR;
  IElementType mBAND = T_BAND;
  IElementType mBAND_ASSIGN = T_BAND_ASSIGN;
  IElementType mLAND = T_LAND;
  IElementType mSEMI = T_SEMI;
  IElementType mDOLLAR = T_DOLLAR;
  IElementType mRANGE_INCLUSIVE = T_RANGE;
  IElementType mRANGE_EXCLUSIVE = T_RANGE_EX;
  IElementType mTRIPLE_DOT = T_ELLIPSIS;
  IElementType mSPREAD_DOT = T_SPREAD_DOT;
  IElementType mOPTIONAL_DOT = T_SAFE_DOT;
  IElementType mMEMBER_POINTER = T_METHOD_CLOSURE;
  IElementType mREGEX_FIND = T_REGEX_FIND;
  IElementType mREGEX_MATCH = T_REGEX_MATCH;
  IElementType mSTAR_STAR = T_POW;
  IElementType mSTAR_STAR_ASSIGN = T_POW_ASSIGN;
  IElementType mCLOSABLE_BLOCK_OP = T_ARROW;
  IElementType mAT = T_AT;

  /* **************************************************************************************************
 *  Keywords (in alphabetic order)
 * ****************************************************************************************************/

  IElementType kABSTRACT = KW_ABSTRACT;
  IElementType kAS = KW_AS;
  IElementType kASSERT = KW_ASSERT;
  IElementType kBOOLEAN = KW_BOOLEAN;
  IElementType kBREAK = KW_BREAK;
  IElementType kBYTE = KW_BYTE;
  IElementType kCASE = KW_CASE;
  IElementType kCATCH = KW_CATCH;
  IElementType kCHAR = KW_CHAR;
  IElementType kCLASS = KW_CLASS;
  IElementType kCONTINUE = KW_CONTINUE;
  IElementType kDEF = KW_DEF;
  IElementType kDEFAULT = KW_DEFAULT;
  IElementType kDO = KW_DO;
  IElementType kDOUBLE = KW_DOUBLE;
  IElementType kELSE = KW_ELSE;
  IElementType kEXTENDS = KW_EXTENDS;
  IElementType kENUM = KW_ENUM;
  IElementType kFALSE = KW_FALSE;
  IElementType kFINAL = KW_FINAL;
  IElementType kFLOAT = KW_FLOAT;
  IElementType kFOR = KW_FOR;
  IElementType kFINALLY = KW_FINALLY;
  IElementType kIF = KW_IF;
  IElementType kIMPLEMENTS = KW_IMPLEMENTS;
  IElementType kIMPORT = KW_IMPORT;
  IElementType kIN = KW_IN;
  IElementType kINSTANCEOF = KW_INSTANCEOF;
  IElementType kINT = KW_INT;
  IElementType kINTERFACE = KW_INTERFACE;
  IElementType kLONG = KW_LONG;
  IElementType kNATIVE = KW_NATIVE;
  IElementType kNEW = KW_NEW;
  IElementType kNULL = KW_NULL;
  IElementType kPACKAGE = KW_PACKAGE;
  IElementType kPRIVATE = KW_PRIVATE;
  IElementType kPROTECTED = KW_PROTECTED;
  IElementType kPUBLIC = KW_PUBLIC;
  IElementType kRETURN = KW_RETURN;
  IElementType kSHORT = KW_SHORT;
  IElementType kSTATIC = KW_STATIC;
  IElementType kSTRICTFP = KW_STRICTFP;
  IElementType kSUPER = KW_SUPER;
  IElementType kSWITCH = KW_SWITCH;
  IElementType kSYNCHRONIZED = KW_SYNCHRONIZED;
  IElementType kTHIS = KW_THIS;
  IElementType kTHROW = KW_THROW;
  IElementType kTHROWS = KW_THROWS;
  IElementType kTRAIT = KW_TRAIT;
  IElementType kTRANSIENT = KW_TRANSIENT;
  IElementType kTRUE = KW_TRUE;
  IElementType kTRY = KW_TRY;
  IElementType kVOID = KW_VOID;
  IElementType kVOLATILE = KW_VOLATILE;
  IElementType kWHILE = KW_WHILE;
}
