// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.utils

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil

internal typealias PsiPointer<E> = SmartPsiElementPointer<E>

inline fun <reified T : PsiElement> PsiElement.filterFor(filter: (T) -> Boolean = { true }): List<T> = PsiTreeUtil.collectElementsOfType(
  this, T::class.java).filter(filter).distinct()

fun ASTNode.noParentOfTypes(tokenSet: TokenSet) = TreeUtil.findParent(this, tokenSet) == null

fun ASTNode.hasType(vararg tokens: IElementType) = hasType(tokens.toSet())
fun ASTNode.hasType(tokens: Set<IElementType>) = this.elementType in tokens

inline fun <reified T : PsiElement> T.toPointer(): PsiPointer<T> = SmartPointerManager.createPointer(this)

fun PsiElement.parents(): Sequence<PsiElement> = generateSequence(this) { it.parent }

fun PsiElementProcessor<in PsiElement>.processElements(element: PsiElement?) {
  ProgressManager.checkCanceled()

  element ?: return

  if (element is PsiCompiledElement || !element.isPhysical) {
    if (!execute(element)) return

    for (child in element.children) {
      processElements(child)
    }

  }
  else {
    element.accept(object : PsiRecursiveElementWalkingVisitor() {
      override fun visitElement(element: PsiElement) {
        if (execute(element)) {
          super.visitElement(element)
        }
      }
    })
  }
}

/**
 * Checks if IntRange is at the beginning of PsiElement
 *
 * @param element PsiElement for range check
 * @param skipWhitespace flag for skipping whitespaces (if true, whitespaces at the beginning are ignored)
 * @return true if IntRange at the beginning of [element]
 */
fun IntRange.isAtStart(element: PsiElement, skipWhitespace: Boolean = true): Boolean {
  var start = 0
  while (start < element.text.length && start !in this && (skipWhitespace && element.text[start].isWhitespace())) {
    start++
  }
  return start in this
}

/**
 * Checks if IntRange is at the end of PsiElement
 *
 * @param element PsiElement for range check
 * @param skipWhitespace flag for skipping whitespaces (if true, whitespaces at the end are ignored)
 * @return true if IntRange at the end of [element]
 */
fun IntRange.isAtEnd(element: PsiElement, skipWhitespace: Boolean = true): Boolean {
  var end = element.text.length - 1
  while (end >= 0 && end !in this && (skipWhitespace && element.text[end].isWhitespace())) {
    end--
  }
  return end in this
}
