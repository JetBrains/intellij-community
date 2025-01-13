// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.reference

import com.intellij.devkit.apiDump.lang.psi.ADPsiElement
import com.intellij.openapi.util.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

internal abstract class ADMemberPsiReference(
  protected val psi: ADPsiElement,
) : PsiReference {

  abstract override fun resolve(): PsiElement?

  override fun getElement(): PsiElement =
    psi

  override fun getRangeInElement(): TextRange =
    TextRange(0, psi.textLength)

  override fun getCanonicalText(): @NlsSafe String =
    psi.text

  override fun handleElementRename(newElementName: String): PsiElement? =
    psi // does not update the underlying PSI

  override fun bindToElement(element: PsiElement): PsiElement? =
    psi // does not update the underlying PSI

  override fun isReferenceTo(element: PsiElement): Boolean =
    resolve() == element

  override fun isSoft(): Boolean =
    true
}

private val refKey = Key.create<PsiReference>("ADReferenceKey")

internal fun ADPsiElement.getReference(init: () -> PsiReference): PsiReference =
  (this as UserDataHolderEx).getOrCreateUserData(refKey, init)