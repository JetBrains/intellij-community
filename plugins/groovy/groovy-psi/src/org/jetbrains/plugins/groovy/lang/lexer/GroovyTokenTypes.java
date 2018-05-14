// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.psi.tree.IElementType;

/**
 * Interface that contains all tokens returned by GroovyLexer
 *
 * @author ilyas
 */
public interface GroovyTokenTypes {

  /**
   * Wrong token. Use for debug needs
   */
  IElementType mWRONG = new GroovyElementType("wrong token");

  /* **************************************************************************************************
 *  Whitespaces & NewLines
 * ****************************************************************************************************/

  IElementType mNLS = new GroovyElementType("new line");

  /* **************************************************************************************************
 *  Comments
 * ****************************************************************************************************/

  IElementType mSH_COMMENT = new GroovyElementType("shell comment");
  IElementType mSL_COMMENT = new GroovyElementType("line comment");
  IElementType mML_COMMENT = new GroovyElementType("block comment");

  /* **************************************************************************************************
 *  Identifiers
 * ****************************************************************************************************/

  IElementType mIDENT = new GroovyElementType("identifier");

  /* **************************************************************************************************
 *  Integers & floats
 * ****************************************************************************************************/

  IElementType mNUM_INT = new GroovyElementType("Integer");
  IElementType mNUM_LONG = new GroovyElementType("Long");
  IElementType mNUM_BIG_INT = new GroovyElementType("BigInteger");
  IElementType mNUM_BIG_DECIMAL = new GroovyElementType("BigDecimal");
  IElementType mNUM_FLOAT = new GroovyElementType("Float");
  IElementType mNUM_DOUBLE = new GroovyElementType("Double");

  /* **************************************************************************************************
 *  Strings & regular expressions
 * ****************************************************************************************************/

  IElementType mSTRING_LITERAL = new GroovyElementType("string");
  IElementType mGSTRING_LITERAL = new GroovyElementType("Gstring");

  IElementType mGSTRING_BEGIN = new GroovyElementType("Gstring begin");
  IElementType mGSTRING_CONTENT = new GroovyElementType("Gstring content");
  IElementType mGSTRING_END = new GroovyElementType("Gstring end");

  IElementType mREGEX_BEGIN = new GroovyElementType("regex begin");
  IElementType mREGEX_CONTENT = new GroovyElementType("regex content");
  IElementType mREGEX_END = new GroovyElementType("regex end");
  IElementType mREGEX_LITERAL = new GroovyElementType("SLASHY_LITERAL");

  IElementType mDOLLAR_SLASH_REGEX_BEGIN = new GroovyElementType("$/ regex begin");
  IElementType mDOLLAR_SLASH_REGEX_CONTENT = new GroovyElementType("$/ regex content");
  IElementType mDOLLAR_SLASH_REGEX_END = new GroovyElementType("$/ regex end");
  IElementType mDOLLAR_SLASH_REGEX_LITERAL = new GroovyElementType("DOLLAR_SLASHY_LITERAL");


  /* **************************************************************************************************
 *  Common tokens: operators, braces etc.
 * ****************************************************************************************************/

  IElementType mQUESTION = new GroovyElementType("?");
  IElementType mDIV = new GroovyElementType("/");
  IElementType mDIV_ASSIGN = new GroovyElementType("/=");
  IElementType mLPAREN = new GroovyElementType("(");
  IElementType mRPAREN = new GroovyElementType(")");
  IElementType mLBRACK = new GroovyElementType("[");
  IElementType mRBRACK = new GroovyElementType("]");
  IElementType mLCURLY = new GroovyElementType("{");
  IElementType mRCURLY = new GroovyElementType("}");
  IElementType mCOLON = new GroovyElementType(":");
  IElementType mCOMMA = new GroovyElementType(",");
  IElementType mDOT = new GroovyElementType(".");
  IElementType mASSIGN = new GroovyElementType("=");
  IElementType mCOMPARE_TO = new GroovyElementType("<=>");
  IElementType mEQUAL = new GroovyElementType("==");
  IElementType mLNOT = new GroovyElementType("!");
  IElementType mELVIS = new GroovyElementType("?:");
  IElementType mBNOT = new GroovyElementType("~");
  IElementType mNOT_EQUAL = new GroovyElementType("!=");
  IElementType mPLUS = new GroovyElementType("+");
  IElementType mPLUS_ASSIGN = new GroovyElementType("+=");
  IElementType mINC = new GroovyElementType("++");
  IElementType mMINUS = new GroovyElementType("-");
  IElementType mMINUS_ASSIGN = new GroovyElementType("-=");
  IElementType mDEC = new GroovyElementType("--");
  IElementType mSTAR = new GroovyElementType("*");
  IElementType mSTAR_ASSIGN = new GroovyElementType("*=");
  IElementType mMOD = new GroovyElementType("%");
  IElementType mMOD_ASSIGN = new GroovyElementType("%=");
  IElementType mBSR_ASSIGN = new GroovyElementType(">>>=");
  IElementType mSR_ASSIGN = new GroovyElementType(">>=");
  IElementType mGE = new GroovyElementType(">=");
  IElementType mGT = new GroovyElementType(">");
  IElementType mSL_ASSIGN = new GroovyElementType("<<=");
  IElementType mLE = new GroovyElementType("<=");
  IElementType mLT = new GroovyElementType("<");
  IElementType mBXOR = new GroovyElementType("^");
  IElementType mBXOR_ASSIGN = new GroovyElementType("^=");
  IElementType mBOR = new GroovyElementType("|");
  IElementType mBOR_ASSIGN = new GroovyElementType("|=");
  IElementType mLOR = new GroovyElementType("||");
  IElementType mBAND = new GroovyElementType("&");
  IElementType mBAND_ASSIGN = new GroovyElementType("&=");
  IElementType mLAND = new GroovyElementType("&&");
  IElementType mSEMI = new GroovyElementType(";");
  IElementType mDOLLAR = new GroovyElementType("$");
  IElementType mRANGE_INCLUSIVE = new GroovyElementType("..");
  IElementType mRANGE_EXCLUSIVE = new GroovyElementType("..<");
  IElementType mTRIPLE_DOT = new GroovyElementType("...");
  IElementType mSPREAD_DOT = new GroovyElementType("*.");
  IElementType mOPTIONAL_DOT = new GroovyElementType("?.");
  IElementType mMEMBER_POINTER = new GroovyElementType(".&");
  IElementType mREGEX_FIND = new GroovyElementType("=~");
  IElementType mREGEX_MATCH = new GroovyElementType("==~");
  IElementType mSTAR_STAR = new GroovyElementType("**");
  IElementType mSTAR_STAR_ASSIGN = new GroovyElementType("**=");
  IElementType mCLOSABLE_BLOCK_OP = new GroovyElementType("->");
  IElementType mAT = new GroovyElementType("@");

  /* **************************************************************************************************
 *  Keywords (in alphabetic order)
 * ****************************************************************************************************/

  IElementType kABSTRACT = new GroovyElementType("abstract");
  IElementType kAS = new GroovyElementType("as");
  IElementType kASSERT = new GroovyElementType("assert");
  IElementType kBOOLEAN = new GroovyElementType("boolean");
  IElementType kBREAK = new GroovyElementType("break");
  IElementType kBYTE = new GroovyElementType("byte");
  IElementType kCASE = new GroovyElementType("case");
  IElementType kCATCH = new GroovyElementType("catch");
  IElementType kCHAR = new GroovyElementType("char");
  IElementType kCLASS = new GroovyElementType("class");
  IElementType kCONTINUE = new GroovyElementType("continue");
  IElementType kDEF = new GroovyElementType("def");
  IElementType kDEFAULT = new GroovyElementType("default");
  IElementType kDO = new GroovyElementType("do");
  IElementType kDOUBLE = new GroovyElementType("double");
  IElementType kELSE = new GroovyElementType("else");
  IElementType kEXTENDS = new GroovyElementType("extends");
  IElementType kENUM = new GroovyElementType("enum");
  IElementType kFALSE = new GroovyElementType("false");
  IElementType kFINAL = new GroovyElementType("final");
  IElementType kFLOAT = new GroovyElementType("float");
  IElementType kFOR = new GroovyElementType("for");
  IElementType kFINALLY = new GroovyElementType("finally");
  IElementType kIF = new GroovyElementType("if");
  IElementType kIMPLEMENTS = new GroovyElementType("implements");
  IElementType kIMPORT = new GroovyElementType("import");
  IElementType kIN = new GroovyElementType("in");
  IElementType kINSTANCEOF = new GroovyElementType("instanceof");
  IElementType kINT = new GroovyElementType("int");
  IElementType kINTERFACE = new GroovyElementType("interface");
  IElementType kLONG = new GroovyElementType("long");
  IElementType kNATIVE = new GroovyElementType("native");
  IElementType kNEW = new GroovyElementType("new");
  IElementType kNULL = new GroovyElementType("null");
  IElementType kPACKAGE = new GroovyElementType("package");
  IElementType kPRIVATE = new GroovyElementType("private");
  IElementType kPROTECTED = new GroovyElementType("protected");
  IElementType kPUBLIC = new GroovyElementType("public");
  IElementType kRETURN = new GroovyElementType("return");
  IElementType kSHORT = new GroovyElementType("short");
  IElementType kSTATIC = new GroovyElementType("static");
  IElementType kSTRICTFP = new GroovyElementType("strictfp");
  IElementType kSUPER = new GroovyElementType("super");
  IElementType kSWITCH = new GroovyElementType("switch");
  IElementType kSYNCHRONIZED = new GroovyElementType("synchronized");
  IElementType kTHIS = new GroovyElementType("this");
  IElementType kTHROW = new GroovyElementType("throw");
  IElementType kTHROWS = new GroovyElementType("throws");
  IElementType kTRAIT = new GroovyElementType("trait");
  IElementType kTRANSIENT = new GroovyElementType("transient");
  IElementType kTRUE = new GroovyElementType("true");
  IElementType kTRY = new GroovyElementType("try");
  IElementType kVOID = new GroovyElementType("void");
  IElementType kVOLATILE = new GroovyElementType("volatile");
  IElementType kWHILE = new GroovyElementType("while");
}
