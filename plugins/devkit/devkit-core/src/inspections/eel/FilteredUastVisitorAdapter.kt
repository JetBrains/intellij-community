// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.eel

import com.intellij.lang.Language
import com.intellij.psi.HintedPsiElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import org.jetbrains.uast.UElement
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

internal fun createFilteredUastVisitor(
  language: Language,
  visitor: AbstractUastNonRecursiveVisitor,
  uElementTypes: Array<Class<out UElement>>,
  isCandidate: (PsiElement) -> Boolean,
): PsiElementVisitor {
  val delegate = UastHintedVisitorAdapter.create(language, visitor, uElementTypes)
  val hintedDelegate = delegate as? HintedPsiElementVisitor ?: return delegate
  return object : PsiElementVisitor(), HintedPsiElementVisitor {
    override fun getHintPsiElements(): List<Class<*>> = hintedDelegate.hintPsiElements

    override fun visitElement(element: PsiElement) {
      if (isCandidate(element)) delegate.visitElement(element)
    }
  }
}
