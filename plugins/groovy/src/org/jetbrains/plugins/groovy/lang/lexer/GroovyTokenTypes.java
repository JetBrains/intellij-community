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

package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

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

  IElementType mWS = new GroovyElementType("white space");
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

  IElementType mGSTRING_SINGLE_BEGIN = new GroovyElementType("Gstring begin");
  IElementType mGSTRING_SINGLE_CONTENT = new GroovyElementType("Gstring content");
  IElementType mGSTRING_SINGLE_END = new GroovyElementType("Gstring end");

  IElementType mWRONG_STRING_LITERAL = new GroovyElementType("wrong string");
  IElementType mWRONG_GSTRING_LITERAL = new GroovyElementType("wrong gstring");

  IElementType mREGEX_LITERAL = new GroovyElementType("regexp");

  IElementType mREGEX_BEGIN = new GroovyElementType("regex begin");
  IElementType mREGEX_CONTENT = new GroovyElementType("regex content");
  IElementType mREGEX_END = new GroovyElementType("regex end");

  IElementType mWRONG_REGEX_LITERAL = new GroovyElementType("wrong regex");

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
 *  Keywords
 * ****************************************************************************************************/

  IElementType kPACKAGE = new GroovyElementType("package");
  IElementType kSTRICTFP = new GroovyElementType("strictfp");
  IElementType kIMPORT = new GroovyElementType("import");
  IElementType kSTATIC = new GroovyElementType("static");
  IElementType kDEF = new GroovyElementType("def");
  IElementType kCLASS = new GroovyElementType("class");
  IElementType kINTERFACE = new GroovyElementType("interface");
  IElementType kENUM = new GroovyElementType("enum");
  IElementType kEXTENDS = new GroovyElementType("extends");
  IElementType kSUPER = new GroovyElementType("super");
  IElementType kVOID = new GroovyElementType("void");
  IElementType kBOOLEAN = new GroovyElementType("boolean");
  IElementType kBYTE = new GroovyElementType("byte");
  IElementType kCHAR = new GroovyElementType("char");
  IElementType kANY = new GroovyElementType("any");
  IElementType kSHORT = new GroovyElementType("short");
  IElementType kINT = new GroovyElementType("int");
  IElementType kFLOAT = new GroovyElementType("float");
  IElementType kLONG = new GroovyElementType("long");
  IElementType kDOUBLE = new GroovyElementType("double");
  IElementType kAS = new GroovyElementType("as");
  IElementType kPRIVATE = new GroovyElementType("private");
  IElementType kPUBLIC = new GroovyElementType("public");
  IElementType kPROTECTED = new GroovyElementType("protected");
  IElementType kABSTRACT = new GroovyElementType("abstract");
  IElementType kTRANSIENT = new GroovyElementType("transient");
  IElementType kNATIVE = new GroovyElementType("native");
  IElementType kSYNCHRONIZED = new GroovyElementType("synchronized");
  IElementType kVOLATILE = new GroovyElementType("volatile");
  IElementType kDEFAULT = new GroovyElementType("default");
  IElementType kTHROWS = new GroovyElementType("throws");
  IElementType kIMPLEMENTS = new GroovyElementType("implements");
  IElementType kTHIS = new GroovyElementType("this");
  IElementType kIF = new GroovyElementType("if");
  IElementType kELSE = new GroovyElementType("else");
  IElementType kWHILE = new GroovyElementType("while");
  IElementType kWITH = new GroovyElementType("with");
  IElementType kSWITCH = new GroovyElementType("switch");
  IElementType kFOR = new GroovyElementType("for");
  IElementType kIN = new GroovyElementType("in");
  IElementType kRETURN = new GroovyElementType("return");
  IElementType kBREAK = new GroovyElementType("break");
  IElementType kCONTINUE = new GroovyElementType("continue");
  IElementType kTHROW = new GroovyElementType("throw");
  IElementType kASSERT = new GroovyElementType("assert");
  IElementType kCASE = new GroovyElementType("case");
  IElementType kTRY = new GroovyElementType("try");
  IElementType kFINALLY = new GroovyElementType("finally");
  IElementType kFINAL = new GroovyElementType("final");
  IElementType kCATCH = new GroovyElementType("catch");
  IElementType kINSTANCEOF = new GroovyElementType("instanceof");
  IElementType kNEW = new GroovyElementType("new");
  IElementType kTRUE = new GroovyElementType("true");
  IElementType kFALSE = new GroovyElementType("false");
  IElementType kNULL = new GroovyElementType("null");

  TokenSet KEYWORDS = TokenSet.create(kPACKAGE, kANY, kIMPORT, kSTATIC, kDEF, kCLASS, kINTERFACE, kENUM, kEXTENDS,
      kSUPER, kVOID, kBOOLEAN, kBYTE, kCHAR, kSHORT, kINT, kFLOAT, kLONG, kDOUBLE, kAS, kPRIVATE, kPUBLIC,
      kPROTECTED, kTRANSIENT, kNATIVE, kSYNCHRONIZED, kVOLATILE, kDEFAULT, kTHROWS, kIMPLEMENTS, kTHIS, kIF,
      kELSE, kWHILE, kWITH, kSWITCH, kFOR, kIN, kRETURN, kBREAK, kCONTINUE, kTHROW, kASSERT, kCASE, kTRY,
      kFINALLY, kCATCH, kINSTANCEOF, kNEW, kTRUE, kFALSE, kNULL);

  TokenSet ASSIGN_OP_SET = TokenSet.create(mASSIGN, mBAND_ASSIGN, mBOR_ASSIGN, mBSR_ASSIGN, mBXOR_ASSIGN,
      mDIV_ASSIGN, mMINUS_ASSIGN, mMOD_ASSIGN, mPLUS_ASSIGN, mSL_ASSIGN, mSR_ASSIGN, mSTAR_ASSIGN, mSTAR_STAR_ASSIGN);

  TokenSet UNARY_OP_SET = TokenSet.create(mBNOT, mLNOT, mMINUS, mDEC, mPLUS, mINC);

  TokenSet BINARY_OP_SET = TokenSet.create(mBAND, mBOR, mBXOR, mDIV, mEQUAL, mGE, mGT, mLAND, mLOR,
      mLT, mLE, mMINUS, mMOD, mPLUS, mSTAR, mSTAR_STAR);

  TokenSet DOTS = TokenSet.create(
      mSPREAD_DOT,
      mOPTIONAL_DOT,
      mMEMBER_POINTER,
      mDOT
  );
}
