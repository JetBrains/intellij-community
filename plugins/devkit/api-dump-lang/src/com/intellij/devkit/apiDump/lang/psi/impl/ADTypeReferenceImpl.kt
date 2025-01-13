// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.psi.impl

import com.intellij.devkit.apiDump.lang.psi.ADTypeReference
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet
import com.intellij.psi.tree.IElementType

internal abstract class ADTypeReferenceImpl(type: IElementType) : ADPsiElementImpl(type), ADTypeReference {

  private val referenceSet: JavaClassReferenceSet
    get() = (this as UserDataHolderEx).getOrCreateUserData(refKey) { ReferenceSet(this) }

  override fun getReferences(): Array<PsiReference> =
    referenceSet.references

  override fun subtreeChanged() {
    val referenceSet = getUserData(refKey) ?: return
    val range = TextRange(0, this.textLength)
    referenceSet.reparse(this, range)
  }
}

private class ReferenceSet(ref: ADTypeReference) : JavaClassReferenceSet(ref.text, ref, 0, true, JavaClassReferenceProvider()) {
  override fun isAllowDollarInNames(): Boolean = true

  override fun createReference(referenceIndex: Int, referenceText: String, textRange: TextRange, staticImport: Boolean): JavaClassReference =
    ADTypePsiReference(this, textRange, referenceIndex, referenceText, staticImport)
}

private class ADTypePsiReference(
  referenceSet: JavaClassReferenceSet,
  range: TextRange,
  index: Int,
  text: String, staticImport: Boolean,
) : JavaClassReference(referenceSet, range, index, text, staticImport) {
  override fun getVariants(): Array<out Any?> = emptyArray()

  override fun isReferenceTo(element: PsiElement): Boolean =
    Registry.`is`("intellij.devkit.api.dump.find.usages") && super.isReferenceTo(element)
}

private val refKey = Key.create<JavaClassReferenceSet>("JavaClassReferenceSetKey")
