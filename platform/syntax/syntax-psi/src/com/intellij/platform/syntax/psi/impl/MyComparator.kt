// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.ASTNode
import com.intellij.lang.ForeignLeafType
import com.intellij.lang.LighterASTNode
import com.intellij.lang.TokenWrapper
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.util.Comparing
import com.intellij.platform.syntax.psi.ExtraWhitespaces
import com.intellij.platform.syntax.psi.impl.PsiSyntaxBuilderImpl.Companion.getErrorMessage
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.TokenType
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.impl.source.tree.*
import com.intellij.psi.tree.*
import com.intellij.util.ThreeState
import com.intellij.util.TripleFunction
import com.intellij.util.diff.FlyweightCapableTreeStructure
import com.intellij.util.diff.ShallowNodeComparator

internal class MyComparator(
  private val treeStructure: MyTreeStructure,
  private val customLanguageASTComparators: MutableList<out CustomLanguageASTComparator>,
  private val custom: TripleFunction<ASTNode, LighterASTNode, FlyweightCapableTreeStructure<LighterASTNode>, ThreeState>?,
) : ShallowNodeComparator<ASTNode?, LighterASTNode?> {
  override fun deepEqual(oldNode: ASTNode, newNode: LighterASTNode): ThreeState {
    ProgressIndicatorProvider.checkCanceled()

    val oldIsErrorElement = oldNode is PsiErrorElement && oldNode.getElementType() === TokenType.ERROR_ELEMENT
    val newIsErrorElement = newNode.getTokenType() === TokenType.ERROR_ELEMENT
    if (oldIsErrorElement != newIsErrorElement) return ThreeState.NO
    if (oldIsErrorElement) {
      val e1 = oldNode as PsiErrorElement
      return if (e1.getErrorDescription() == getErrorMessage(newNode)) ThreeState.UNSURE else ThreeState.NO
    }

    val customResult = customCompare(oldNode, newNode)
    if (customResult != ThreeState.UNSURE) {
      return customResult
    }
    if (newNode is Token) {
      val type = newNode.getTokenType()
      val token = newNode

      if (oldNode is ForeignLeafPsiElement) {
        return if (type is ForeignLeafType && type.text == oldNode.getText())
          ThreeState.YES
        else
          ThreeState.NO
      }

      if (oldNode is LeafElement) {
        if (type is ForeignLeafType) return ThreeState.NO

        return if (oldNode.textMatches(token.getText()))
          ThreeState.YES
        else
          ThreeState.NO
      }

      if (type is ILightLazyParseableElementType) {
        if ((oldNode as TreeElement).textMatches(token.getText())) {
          return if (PsiDocumentManagerBase.isFullReparseInProgress()) ThreeState.UNSURE else ThreeState.YES
        }
        return if (TreeUtil.isCollapsedChameleon(oldNode))
          ThreeState.NO // do not dive into collapsed nodes
        else
          ThreeState.UNSURE
      }

      if (oldNode.getElementType() is ILazyParseableElementType && type is ILazyParseableElementType ||
          oldNode.getElementType() is ICustomParsingType && type is ICustomParsingType
      ) {
        return if ((oldNode as TreeElement).textMatches(token.getText()))
          ThreeState.YES
        else
          ThreeState.NO
      }
    }

    return ThreeState.UNSURE
  }

  fun customCompare(oldNode: ASTNode, newNode: LighterASTNode): ThreeState {
    for (comparator in customLanguageASTComparators) {
      val customComparatorResult = comparator.compareAST(oldNode, newNode, treeStructure)
      if (customComparatorResult != ThreeState.UNSURE) {
        return customComparatorResult
      }
    }

    if (custom != null) {
      return custom.`fun`(oldNode, newNode, treeStructure)
    }

    return ThreeState.UNSURE
  }

  override fun typesEqual(n1: ASTNode, n2: LighterASTNode): Boolean {
    if (n1 is PsiWhiteSpaceImpl) {
      return ExtraWhitespaces.whitespaces.contains(n2.getTokenType()) ||
             n2 is Token && n2.nodeData.whitespaceTokens.contains(n2.getTokenType())
    }
    val n1t: IElementType?
    val n2t: IElementType?
    if (n1 is ForeignLeafPsiElement) {
      n1t = n1.foreignType
      n2t = n2.getTokenType()
    }
    else {
      n1t = dereferenceToken(n1.getElementType())
      n2t = dereferenceToken(n2.getTokenType())
    }

    return Comparing.equal(n1t, n2t)
  }

  override fun hashCodesEqual(n1: ASTNode, n2: LighterASTNode): Boolean {
    if (n1 is LeafElement && n2 is Token) {
      val isForeign1 = n1 is ForeignLeafPsiElement
      val isForeign2 = n2.getTokenType() is ForeignLeafType
      if (isForeign1 != isForeign2) return false

      if (isForeign1) {
        return n1.getText() == (n2.getTokenType() as ForeignLeafType).text
      }

      return n1.textMatches(n2.getText())
    }

    if (n1 is PsiErrorElement && n2.getTokenType() === TokenType.ERROR_ELEMENT) {
      val e1 = n1 as PsiErrorElement
      if (e1.getErrorDescription() != getErrorMessage(n2)) return false
    }

    return (n2 as Node).tokenTextMatches(n1.getChars())
  }

  companion object {
    private fun dereferenceToken(probablyWrapper: IElementType?): IElementType? {
      if (probablyWrapper is TokenWrapper) {
        return dereferenceToken(probablyWrapper.delegate)
      }
      return probablyWrapper
    }
  }
}