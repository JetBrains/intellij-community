// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.reference

import com.intellij.devkit.apiDump.lang.psi.ADPsiElement
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.light.LightElement

internal abstract class ADMemberPsiReference(
  protected val psi: ADPsiElement,
) : PsiReference {

  abstract fun resolveRaw(): PsiElement?

  final override fun resolve(): PsiElement? {
    val resolved = resolveRaw() ?: return null
    if (Registry.`is`("intellij.devkit.api.dump.find.usages")) {
      return resolved
    }
    else {
      return OpaqueWrapper(resolved)
    }
  }

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
    Registry.`is`("intellij.devkit.api.dump.find.usages") && psi.manager.areElementsEquivalent(resolve(), element)

  override fun isSoft(): Boolean =
    false
}

private val refKey = Key.create<PsiReference>("ADReferenceKey")

internal fun ADPsiElement.getReference(init: () -> PsiReference): PsiReference =
  (this as UserDataHolderEx).getOrCreateUserData(refKey, init)

/**
 * We need to wrap targets of references to this wrapper when "find usages" is disabled for api dumps.
 * If we don't use a wrapper, find usage searcher for methods overcomes isReferenceTo returns false
 */
private class OpaqueWrapper(val psi: PsiElement) : LightElement(psi.manager, psi.language) {
  override fun toString(): String = "Wrapper($psi)"

  override fun getNavigationElement(): PsiElement = psi

  override fun getText(): String? = psi.text
}
