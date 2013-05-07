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
package org.jetbrains.plugins.groovy.formatter.processors;

import com.intellij.formatting.Wrap;
import com.intellij.formatting.WrapType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.GroovyBlock;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;

import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;

/**
 * @author Max Medvedev
 */
public class GroovyWrappingProcessor {
  private final ASTNode myNode;
  private final CommonCodeStyleSettings mySettings;
  private final IElementType myParentType;
  private final Wrap myCommonWrap;

  public GroovyWrappingProcessor(GroovyBlock block) {
    mySettings = block.getContext().getSettings();
    myNode = block.getNode();
    myParentType = myNode.getElementType();

    myCommonWrap = createCommonWrap();
  }
  
  private static final TokenSet SKIP = TokenSet.create(
    mCOMMA, mQUESTION, mSEMI,

    mASSIGN, mBAND_ASSIGN, mBOR_ASSIGN, mBSR_ASSIGN, mBXOR_ASSIGN, mDIV_ASSIGN,
    mMINUS_ASSIGN, mMOD_ASSIGN, mPLUS_ASSIGN, mSL_ASSIGN, mSR_ASSIGN,
    mSTAR_ASSIGN, mSTAR_STAR_ASSIGN,

    mASSIGN, mBAND_ASSIGN, mBOR_ASSIGN, mBSR_ASSIGN, mBXOR_ASSIGN, mDIV_ASSIGN,
    mMINUS_ASSIGN, mMOD_ASSIGN, mPLUS_ASSIGN, mSL_ASSIGN, mSR_ASSIGN,
    mSTAR_ASSIGN, mSTAR_STAR_ASSIGN,

    mBAND, mBOR, mBXOR, mDIV, mEQUAL, mGE, mGT, mLOR, mLT, mLE, mMINUS, kAS, kIN,
    mMOD, mPLUS, mSTAR, mSTAR_STAR, mNOT_EQUAL, mCOMPARE_TO, mLAND, kINSTANCEOF,
    COMPOSITE_LSHIFT_SIGN, COMPOSITE_RSHIFT_SIGN, COMPOSITE_TRIPLE_SHIFT_SIGN,
    mREGEX_FIND, mREGEX_MATCH, mRANGE_INCLUSIVE, mRANGE_EXCLUSIVE,

    mBNOT, mLNOT, mMINUS, mDEC, mPLUS, mINC,

    mSPREAD_DOT, mOPTIONAL_DOT, mMEMBER_POINTER, mDOT,

    COMPOSITE_LSHIFT_SIGN, COMPOSITE_RSHIFT_SIGN, COMPOSITE_TRIPLE_SHIFT_SIGN,

    mLT, mGT, mLE, mGE, kIN,

    kIN, mCOLON,

    mGSTRING_CONTENT, mGSTRING_END, GSTRING_INJECTION, mREGEX_CONTENT, mREGEX_END, mDOLLAR_SLASH_REGEX_CONTENT, mDOLLAR_SLASH_REGEX_END
  );

  public Wrap getChildWrap(ASTNode childNode) {
    final IElementType childType = childNode.getElementType();
    
    if (SKIP.contains(childType)) {
      return Wrap.createWrap(WrapType.NONE, false);
    }

    if (myParentType == EXTENDS_CLAUSE || myParentType == IMPLEMENTS_CLAUSE) {
      if (childType == kEXTENDS || childType == kIMPLEMENTS) {
        return Wrap.createWrap(mySettings.EXTENDS_KEYWORD_WRAP, true);
      }
    }


    if (myParentType == THROW_CLAUSE && childType == kTHROWS) {
      return Wrap.createWrap(mySettings.THROWS_KEYWORD_WRAP, true);
    }

    if (myParentType == CLOSABLE_BLOCK) {
      return Wrap.createWrap(WrapType.NONE, false);
    }

    if (myParentType == PARAMETERS_LIST) {
      if (childType == ANNOTATION) {
        return myCommonWrap;
      }
      else {
        return createNormalWrap();
      }
    }

    if (myCommonWrap != null) {
      return myCommonWrap;
    }
    else {
      return createNormalWrap();
    }
  }

  private static Wrap createNormalWrap() {
    return Wrap.createWrap(WrapType.NORMAL, true);
  }

  @Nullable
  private Wrap createCommonWrap() {
    if (myParentType == EXTENDS_CLAUSE || myParentType == IMPLEMENTS_CLAUSE) {
      return Wrap.createWrap(mySettings.EXTENDS_LIST_WRAP, true);
    }


    if (myParentType == THROW_CLAUSE) {
      return Wrap.createWrap(mySettings.THROWS_LIST_WRAP, true);
    }


    if (myParentType == PARAMETERS_LIST) {
      return Wrap.createWrap(mySettings.METHOD_PARAMETERS_WRAP, true);
    }


    if (myParentType == ARGUMENTS) {
      return Wrap.createWrap(mySettings.CALL_PARAMETERS_WRAP, true);
    }


    if (myParentType == FOR_TRADITIONAL_CLAUSE || myParentType == FOR_IN_CLAUSE) {
      return Wrap.createWrap(mySettings.FOR_STATEMENT_WRAP, true);
    }


    if (TokenSets.BINARY_EXPRESSIONS.contains(myParentType)) {
      return Wrap.createWrap(mySettings.BINARY_OPERATION_WRAP, true);
    }


    if (myParentType == ASSIGNMENT_EXPRESSION) {
      return Wrap.createWrap(mySettings.ASSIGNMENT_WRAP, true);
    }


    if (myParentType == CONDITIONAL_EXPRESSION || myParentType == ELVIS_EXPRESSION) {
      return Wrap.createWrap(mySettings.TERNARY_OPERATION_WRAP, true);
    }


    if (myParentType == ASSERT_STATEMENT) {
      return Wrap.createWrap(mySettings.ASSERT_STATEMENT_WRAP, true);
    }

    if (myParentType == GSTRING_INJECTION) {
      return Wrap.createWrap(WrapType.NONE, false);
    }

    if (myParentType == PARAMETERS_LIST) {
      final IElementType pparentType = myNode.getTreeParent().getElementType();
      if (TYPE_DEFINITION_TYPES.contains(pparentType)) {
        return Wrap.createWrap(mySettings.CLASS_ANNOTATION_WRAP, true);
      }

      if (METHOD_DEFS.contains(pparentType)) {
        return Wrap.createWrap(mySettings.METHOD_ANNOTATION_WRAP, true);
      }

      if (VARIABLE_DEFINITION == pparentType) {
        final IElementType ppparentType = myNode.getTreeParent().getTreeParent().getElementType();
        if (ppparentType == CLASS_BODY || ppparentType == ENUM_BODY) {
          return Wrap.createWrap(mySettings.FIELD_ANNOTATION_WRAP, true);
        }
        else {
          return Wrap.createWrap(mySettings.VARIABLE_ANNOTATION_WRAP, true);
        }
      }

      if (PARAMETER == pparentType) {
        return Wrap.createWrap(mySettings.PARAMETER_ANNOTATION_WRAP, true);
      }

      if (ENUM_CONSTANT == pparentType) {
        return Wrap.createWrap(mySettings.ENUM_CONSTANTS_WRAP, true);
      }
    }

    return null;
  }

  public Wrap getChainedMethodCallWrap() {
    return Wrap.createWrap(mySettings.METHOD_CALL_CHAIN_WRAP, true);
  }
}
