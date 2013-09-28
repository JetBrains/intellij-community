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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.formatter.FormattingContext;
import org.jetbrains.plugins.groovy.formatter.blocks.GroovyBlock;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

import static org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.*;

/**
 * @author Max Medvedev
 */
public class GroovyWrappingProcessor {
  private final ASTNode myNode;
  private final CommonCodeStyleSettings mySettings;
  private final IElementType myParentType;
  private final Wrap myCommonWrap;
  private final FormattingContext myContext;
  private boolean myUsedDefaultWrap = false;

  public GroovyWrappingProcessor(GroovyBlock block) {
    myContext = block.getContext();
    mySettings = myContext.getSettings();
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
    if (myContext.isInsidePlainGString()) return createNoneWrap();

    final IElementType childType = childNode.getElementType();

    if (SKIP.contains(childType)) {
      return createNoneWrap();
    }

    if (myParentType == EXTENDS_CLAUSE || myParentType == IMPLEMENTS_CLAUSE) {
      if (childType == kEXTENDS || childType == kIMPLEMENTS) {
        return Wrap.createWrap(mySettings.EXTENDS_KEYWORD_WRAP, true);
      }
    }

    if (myParentType == ARGUMENTS) {
      if (childType == mLPAREN  || childType == mRPAREN) {
        return createNoneWrap();
      }
    }

    if (myParentType == THROW_CLAUSE && childType == kTHROWS) {
      return Wrap.createWrap(mySettings.THROWS_KEYWORD_WRAP, true);
    }

    if (myParentType == MODIFIERS) {
      if (getLeftSiblingType(childNode) == ANNOTATION) {
        return getCommonWrap();
      }
      else {
        return createNormalWrap();
      }
    }

    if (ANNOTATION_CONTAINERS.contains(myParentType)) {
      final ASTNode leftSibling = getLeftSibling(childNode);
      if (leftSibling != null && leftSibling.getElementType() == MODIFIERS && endsWithAnnotation(leftSibling)) {
        final int wrapType = getAnnotationsWrapType(childNode);
        if (wrapType != -1) {
          return Wrap.createWrap(wrapType, true);
        }
      }
    }

    return getCommonWrap();
  }

  @Nullable
  private static IElementType getLeftSiblingType(ASTNode node) {
    ASTNode prev = getLeftSibling(node);
    return prev != null ? prev.getElementType() : null;
  }

  private static ASTNode getLeftSibling(ASTNode node) {
    ASTNode prev = node.getTreePrev();
    while (prev != null && StringUtil.isEmptyOrSpaces(prev.getText())) {
      prev = prev.getTreePrev();
    }
    return prev;
  }

  private static boolean endsWithAnnotation(ASTNode modifierListNode) {
    final PsiElement psi = modifierListNode.getPsi();
    return psi instanceof GrModifierList && psi.getLastChild() instanceof GrAnnotation;
  }

  private Wrap getCommonWrap() {
    if (myCommonWrap == null) {
      return createNoneWrap();
      //return null;
    }

    if (myUsedDefaultWrap) {
      return myCommonWrap;
    }
    else {
      myUsedDefaultWrap = true;
      return createNoneWrap();
      //return null;
    }
  }

  private static Wrap createNormalWrap() {
    return Wrap.createWrap(WrapType.NORMAL, true);
  }

  private static Wrap createNoneWrap() {
    return Wrap.createWrap(WrapType.NONE, false);
  }

  @Nullable
  private Wrap createCommonWrap() {
    if (myParentType == EXTENDS_CLAUSE || myParentType == IMPLEMENTS_CLAUSE) {
      myUsedDefaultWrap = true;
      return Wrap.createWrap(mySettings.EXTENDS_LIST_WRAP, true);
    }


    if (myParentType == THROW_CLAUSE) {
      myUsedDefaultWrap = true;
      return Wrap.createWrap(mySettings.THROWS_LIST_WRAP, true);
    }


    if (myParentType == PARAMETERS_LIST) {
      myUsedDefaultWrap = true;
      return Wrap.createWrap(mySettings.METHOD_PARAMETERS_WRAP, true);
    }


    if (myParentType == ARGUMENTS || myParentType == COMMAND_ARGUMENTS) {
      myUsedDefaultWrap = myParentType == ARGUMENTS;
      return Wrap.createWrap(mySettings.CALL_PARAMETERS_WRAP, myUsedDefaultWrap);
    }


    if (myParentType == FOR_TRADITIONAL_CLAUSE || myParentType == FOR_IN_CLAUSE) {
      myUsedDefaultWrap = true;
      return Wrap.createWrap(mySettings.FOR_STATEMENT_WRAP, true);
    }


    if (TokenSets.BINARY_EXPRESSIONS.contains(myParentType)) {
      return Wrap.createWrap(mySettings.BINARY_OPERATION_WRAP, false);
    }


    if (myParentType == ASSIGNMENT_EXPRESSION) {
      return Wrap.createWrap(mySettings.ASSIGNMENT_WRAP, false);
    }


    if (myParentType == CONDITIONAL_EXPRESSION || myParentType == ELVIS_EXPRESSION) {
      return Wrap.createWrap(mySettings.TERNARY_OPERATION_WRAP, false);
    }

    if (myParentType == ASSERT_STATEMENT) {
      return Wrap.createWrap(mySettings.ASSERT_STATEMENT_WRAP, false);
    }

    if (TokenSets.BLOCK_SET.contains(myParentType)) {
      return createNormalWrap();
    }

    if (myParentType == MODIFIERS) {
      final int wrapType = getAnnotationsWrapType(myNode);
      if (wrapType != -1) {
        myUsedDefaultWrap = true;
        return Wrap.createWrap(wrapType, true);
      }
    }

    return null;
  }

  public Wrap getChainedMethodCallWrap() {
    return Wrap.createWrap(mySettings.METHOD_CALL_CHAIN_WRAP, false);
  }

  private TokenSet ANNOTATION_CONTAINERS = TokenSet.create(
    CLASS_DEFINITION, INTERFACE_DEFINITION, ENUM_DEFINITION, ANNOTATION_DEFINITION,
    METHOD_DEFINITION, CONSTRUCTOR_DEFINITION,
    VARIABLE_DEFINITION,
    PARAMETER,
    ENUM_CONSTANT,
    IMPORT_STATEMENT
  );

  private int getAnnotationsWrapType(ASTNode modifierList) {
    final IElementType containerType = modifierList.getTreeParent().getElementType();
    if (TYPE_DEFINITION_TYPES.contains(containerType)) {
      return mySettings.CLASS_ANNOTATION_WRAP;
    }

    if (TokenSets.METHOD_DEFS.contains(containerType)) {
      return mySettings.METHOD_ANNOTATION_WRAP;
    }

    if (VARIABLE_DEFINITION == containerType) {
      final IElementType pparentType = modifierList.getTreeParent().getTreeParent().getElementType();
      if (pparentType == CLASS_BODY || pparentType == ENUM_BODY) {
        return mySettings.FIELD_ANNOTATION_WRAP;
      }
      else {
        return mySettings.VARIABLE_ANNOTATION_WRAP;
      }
    }

    if (PARAMETER == containerType) {
      return mySettings.PARAMETER_ANNOTATION_WRAP;
    }

    if (ENUM_CONSTANT == containerType) {
      return mySettings.ENUM_CONSTANTS_WRAP;
    }

    if (IMPORT_STATEMENT == containerType) {
      return myContext.getGroovySettings().IMPORT_ANNOTATION_WRAP;
    }

    return -1;
  }

}
