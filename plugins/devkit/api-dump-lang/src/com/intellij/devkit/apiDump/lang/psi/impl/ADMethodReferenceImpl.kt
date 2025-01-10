// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.psi.impl

import com.intellij.devkit.apiDump.lang.psi.ADClassDeclaration
import com.intellij.devkit.apiDump.lang.psi.ADMethod
import com.intellij.devkit.apiDump.lang.psi.ADMethodReference
import com.intellij.devkit.apiDump.lang.psi.updateText
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.tree.IElementType

internal abstract class ADMethodReferenceImpl(type: IElementType) : ADPsiElementImpl(type), ADMethodReference, PsiReference {
  override fun resolve(): PsiElement? {
    val method = parent as? ADMethod ?: return null
    val classDeclaration = method.parent as? ADClassDeclaration ?: return null
    val parameters = method.parameters.parameterList.map { parameter -> parameter.text }

    val clazz = classDeclaration.resolvePsiClass() ?: return null
    val methods = clazz.findMethodsByName(this.text, false)

    return methods.firstOrNull { method -> parametersMatch(method.parameters, parameters) }
  }

  override fun getReference(): PsiReference? =
    this

  override fun getElement(): PsiElement =
    this

  override fun getRangeInElement(): TextRange =
    TextRange(0, textLength)

  override fun getCanonicalText(): @NlsSafe String =
    text

  override fun handleElementRename(newElementName: String): PsiElement? =
    updateText(newElementName)

  override fun bindToElement(element: PsiElement): PsiElement? =
    throw UnsupportedOperationException()

  override fun isReferenceTo(element: PsiElement): Boolean =
    resolve() == element

  override fun isSoft(): Boolean =
    true
}
