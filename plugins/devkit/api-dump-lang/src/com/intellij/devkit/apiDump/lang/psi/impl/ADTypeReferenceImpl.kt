// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.psi.impl

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet
import com.intellij.psi.tree.IElementType

internal open class ADTypeReferenceImpl(type: IElementType) : ADPsiElementImpl(type) {

  private val referenceSet: JavaClassReferenceSet
    get() = (this as UserDataHolderEx).getOrCreateUserData(refKey) {
      val provider = JavaClassReferenceProvider()
      val referenceSet = JavaClassReferenceSet(text, this, 0, true, provider)
      referenceSet
    }

  override fun getReferences(): Array<PsiReference> =
    referenceSet.references

  override fun subtreeChanged() {
    val referenceSet = getUserData(refKey) ?: return
    val range = TextRange(0, this.textLength)
    referenceSet.reparse(this, range)
  }
}

private val refKey = Key.create<JavaClassReferenceSet>("JavaClassReferenceSetKey")
