// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

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
    GroovyTokenTypes.mCOMMA, GroovyTokenTypes.mQUESTION, GroovyTokenTypes.mSEMI,

    GroovyTokenTypes.mASSIGN, GroovyTokenTypes.mBAND_ASSIGN, GroovyTokenTypes.mBOR_ASSIGN, GroovyTokenTypes.mBSR_ASSIGN,
    GroovyTokenTypes.mBXOR_ASSIGN, GroovyTokenTypes.mDIV_ASSIGN,
    GroovyTokenTypes.mMINUS_ASSIGN, GroovyTokenTypes.mMOD_ASSIGN, GroovyTokenTypes.mPLUS_ASSIGN, GroovyTokenTypes.mSL_ASSIGN,
    GroovyTokenTypes.mSR_ASSIGN,
    GroovyTokenTypes.mSTAR_ASSIGN, GroovyTokenTypes.mSTAR_STAR_ASSIGN,

    GroovyTokenTypes.mASSIGN, GroovyTokenTypes.mBAND_ASSIGN, GroovyTokenTypes.mBOR_ASSIGN, GroovyTokenTypes.mBSR_ASSIGN,
    GroovyTokenTypes.mBXOR_ASSIGN, GroovyTokenTypes.mDIV_ASSIGN,
    GroovyTokenTypes.mMINUS_ASSIGN, GroovyTokenTypes.mMOD_ASSIGN, GroovyTokenTypes.mPLUS_ASSIGN, GroovyTokenTypes.mSL_ASSIGN,
    GroovyTokenTypes.mSR_ASSIGN,
    GroovyTokenTypes.mSTAR_ASSIGN, GroovyTokenTypes.mSTAR_STAR_ASSIGN,

    GroovyTokenTypes.mBAND, GroovyTokenTypes.mBOR, GroovyTokenTypes.mBXOR, GroovyTokenTypes.mDIV, GroovyTokenTypes.mEQUAL,
    GroovyTokenTypes.mGE, GroovyTokenTypes.mGT, GroovyTokenTypes.mLOR, GroovyTokenTypes.mLT, GroovyTokenTypes.mLE, GroovyTokenTypes.mMINUS,
    GroovyTokenTypes.kAS, GroovyTokenTypes.kIN,
    GroovyTokenTypes.mMOD, GroovyTokenTypes.mPLUS, GroovyTokenTypes.mSTAR, GroovyTokenTypes.mSTAR_STAR, GroovyTokenTypes.mNOT_EQUAL,
    GroovyTokenTypes.mCOMPARE_TO, GroovyTokenTypes.mLAND, GroovyTokenTypes.kINSTANCEOF,
    GroovyElementTypes.COMPOSITE_LSHIFT_SIGN, GroovyElementTypes.COMPOSITE_RSHIFT_SIGN, GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN,
    GroovyTokenTypes.mREGEX_FIND, GroovyTokenTypes.mREGEX_MATCH, GroovyTokenTypes.mRANGE_INCLUSIVE, GroovyTokenTypes.mRANGE_EXCLUSIVE,

    GroovyTokenTypes.mBNOT, GroovyTokenTypes.mLNOT, GroovyTokenTypes.mMINUS, GroovyTokenTypes.mDEC, GroovyTokenTypes.mPLUS,
    GroovyTokenTypes.mINC,

    GroovyTokenTypes.mSPREAD_DOT, GroovyTokenTypes.mOPTIONAL_DOT, GroovyTokenTypes.mMEMBER_POINTER, GroovyTokenTypes.mDOT,

    GroovyElementTypes.COMPOSITE_LSHIFT_SIGN, GroovyElementTypes.COMPOSITE_RSHIFT_SIGN, GroovyElementTypes.COMPOSITE_TRIPLE_SHIFT_SIGN,

    GroovyTokenTypes.mLT, GroovyTokenTypes.mGT, GroovyTokenTypes.mLE, GroovyTokenTypes.mGE, GroovyTokenTypes.kIN,

    GroovyTokenTypes.kIN, GroovyTokenTypes.mCOLON,

    GroovyTokenTypes.mGSTRING_CONTENT, GroovyTokenTypes.mGSTRING_END, GroovyElementTypes.GSTRING_INJECTION, GroovyTokenTypes.mREGEX_CONTENT,
    GroovyTokenTypes.mREGEX_END, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_CONTENT, GroovyTokenTypes.mDOLLAR_SLASH_REGEX_END
  );

  public Wrap getChildWrap(ASTNode childNode) {
    if (myContext.isInsidePlainGString()) return createNoneWrap();

    final IElementType childType = childNode.getElementType();

    if (SKIP.contains(childType)) {
      return createNoneWrap();
    }

    if (myParentType == GroovyElementTypes.EXTENDS_CLAUSE || myParentType == GroovyElementTypes.IMPLEMENTS_CLAUSE) {
      if (childType == GroovyTokenTypes.kEXTENDS || childType == GroovyTokenTypes.kIMPLEMENTS) {
        return Wrap.createWrap(mySettings.EXTENDS_KEYWORD_WRAP, true);
      }
    }

    if (myParentType == GroovyElementTypes.ARGUMENTS) {
      if (childType == GroovyTokenTypes.mLPAREN || childType == GroovyTokenTypes.mRPAREN) {
        return createNoneWrap();
      }
    }

    if (myParentType == GroovyElementTypes.THROW_CLAUSE && childType == GroovyTokenTypes.kTHROWS) {
      return Wrap.createWrap(mySettings.THROWS_KEYWORD_WRAP, true);
    }

    if (myParentType == GroovyElementTypes.MODIFIERS) {
      if (getLeftSiblingType(childNode) == GroovyElementTypes.ANNOTATION) {
        return getCommonWrap();
      }
      else {
        return createNormalWrap();
      }
    }

    if (ANNOTATION_CONTAINERS.contains(myParentType)) {
      final ASTNode leftSibling = getLeftSibling(childNode);
      if (leftSibling != null && leftSibling.getElementType() == GroovyElementTypes.MODIFIERS && endsWithAnnotation(leftSibling)) {
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
    if (myParentType == GroovyElementTypes.EXTENDS_CLAUSE || myParentType == GroovyElementTypes.IMPLEMENTS_CLAUSE) {
      myUsedDefaultWrap = true;
      return Wrap.createWrap(mySettings.EXTENDS_LIST_WRAP, true);
    }


    if (myParentType == GroovyElementTypes.THROW_CLAUSE) {
      myUsedDefaultWrap = true;
      return Wrap.createWrap(mySettings.THROWS_LIST_WRAP, true);
    }


    if (myParentType == GroovyElementTypes.PARAMETERS_LIST) {
      myUsedDefaultWrap = true;
      return Wrap.createWrap(mySettings.METHOD_PARAMETERS_WRAP, true);
    }

    if (myParentType == GroovyElementTypes.ARGUMENTS || myParentType == GroovyElementTypes.COMMAND_ARGUMENTS) {
      myUsedDefaultWrap = true;
      return Wrap.createWrap(mySettings.CALL_PARAMETERS_WRAP, false);
    }

    if (myParentType == GroovyElementTypes.FOR_TRADITIONAL_CLAUSE || myParentType == GroovyElementTypes.FOR_IN_CLAUSE) {
      myUsedDefaultWrap = true;
      return Wrap.createWrap(mySettings.FOR_STATEMENT_WRAP, true);
    }


    if (TokenSets.BINARY_EXPRESSIONS.contains(myParentType)) {
      return Wrap.createWrap(mySettings.BINARY_OPERATION_WRAP, false);
    }

    if (myParentType == GroovyElementTypes.ASSIGNMENT_EXPRESSION ||
        myParentType == GroovyElementTypes.TUPLE_ASSIGNMENT_EXPRESSION) {
      return Wrap.createWrap(mySettings.ASSIGNMENT_WRAP, false);
    }

    if (myParentType == GroovyElementTypes.CONDITIONAL_EXPRESSION || myParentType == GroovyElementTypes.ELVIS_EXPRESSION) {
      return Wrap.createWrap(mySettings.TERNARY_OPERATION_WRAP, false);
    }

    if (myParentType == GroovyElementTypes.ASSERT_STATEMENT) {
      return Wrap.createWrap(mySettings.ASSERT_STATEMENT_WRAP, false);
    }

    if (TokenSets.BLOCK_SET.contains(myParentType)) {
      return createNormalWrap();
    }

    if (myParentType == GroovyElementTypes.MODIFIERS) {
      final int wrapType = getAnnotationsWrapType(myNode);
      if (wrapType != -1) {
        myUsedDefaultWrap = true;
        return Wrap.createWrap(wrapType, true);
      }
    }

    return null;
  }

  public Wrap getChainedMethodCallWrap() {
    return myContext.isInsidePlainGString() ? Wrap.createWrap(WrapType.NONE, false)
                                            : Wrap.createWrap(mySettings.METHOD_CALL_CHAIN_WRAP, false);
  }

  private final TokenSet ANNOTATION_CONTAINERS = TokenSet.create(
    GroovyElementTypes.CLASS_DEFINITION, GroovyElementTypes.INTERFACE_DEFINITION, GroovyElementTypes.ENUM_DEFINITION, GroovyElementTypes.TRAIT_DEFINITION,
    GroovyElementTypes.ANNOTATION_DEFINITION,
    GroovyElementTypes.METHOD_DEFINITION, GroovyElementTypes.CONSTRUCTOR_DEFINITION,
    GroovyElementTypes.VARIABLE_DEFINITION,
    GroovyElementTypes.PARAMETER,
    GroovyElementTypes.ENUM_CONSTANT,
    GroovyElementTypes.IMPORT_STATEMENT
  );

  private int getAnnotationsWrapType(ASTNode modifierList) {
    final IElementType containerType = modifierList.getTreeParent().getElementType();
    if (TokenSets.TYPE_DEFINITIONS.contains(containerType)) {
      return mySettings.CLASS_ANNOTATION_WRAP;
    }

    if (TokenSets.METHOD_DEFS.contains(containerType)) {
      return mySettings.METHOD_ANNOTATION_WRAP;
    }

    if (GroovyElementTypes.VARIABLE_DEFINITION == containerType) {
      final IElementType pparentType = modifierList.getTreeParent().getTreeParent().getElementType();
      if (pparentType == GroovyElementTypes.CLASS_BODY || pparentType == GroovyElementTypes.ENUM_BODY) {
        return mySettings.FIELD_ANNOTATION_WRAP;
      }
      else {
        return mySettings.VARIABLE_ANNOTATION_WRAP;
      }
    }

    if (GroovyElementTypes.PARAMETER == containerType) {
      return mySettings.PARAMETER_ANNOTATION_WRAP;
    }

    if (GroovyElementTypes.ENUM_CONSTANT == containerType) {
      return mySettings.ENUM_CONSTANTS_WRAP;
    }

    if (GroovyElementTypes.IMPORT_STATEMENT == containerType) {
      return myContext.getGroovySettings().IMPORT_ANNOTATION_WRAP;
    }

    return -1;
  }

}
