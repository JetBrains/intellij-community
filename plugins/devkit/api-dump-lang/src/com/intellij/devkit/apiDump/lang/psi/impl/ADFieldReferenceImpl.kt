// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.psi.impl

import com.intellij.devkit.apiDump.lang.psi.ADClassDeclaration
import com.intellij.devkit.apiDump.lang.psi.ADFieldReference
import com.intellij.devkit.apiDump.lang.reference.ADMemberPsiReference
import com.intellij.devkit.apiDump.lang.reference.getReference
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.tree.IElementType

internal abstract class ADFieldReferenceImpl(type: IElementType) : ADPsiElementImpl(type), ADFieldReference {
  override fun getReference(): PsiReference =
    getReference { ADFieldPsiReference(this) }
}

private class ADFieldPsiReference(psi: ADFieldReference) : ADMemberPsiReference(psi) {
  override fun resolveRaw(): PsiElement? {
    val classDeclaration = psi.parent?.parent as? ADClassDeclaration ?: return null
    val clazz = classDeclaration.resolvePsiClass() ?: return null
    return clazz.findFieldByName(psi.text, false)
  }
}