// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.psi.impl

import com.intellij.devkit.apiDump.lang.psi.ADClassDeclaration
import com.intellij.devkit.apiDump.lang.psi.ADMethod
import com.intellij.devkit.apiDump.lang.psi.ADMethodReference
import com.intellij.devkit.apiDump.lang.reference.ADMemberPsiReference
import com.intellij.devkit.apiDump.lang.reference.getReference
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.tree.IElementType

internal abstract class ADMethodReferenceImpl(type: IElementType) : ADPsiElementImpl(type), ADMethodReference {
  override fun getReference(): PsiReference? =
    getReference { ADMethodPsiReference(this) }
}

private class ADMethodPsiReference(psi: ADMethodReference) : ADMemberPsiReference(psi) {
  override fun resolveRaw(): PsiElement? {
    val method = psi.parent as? ADMethod ?: return null
    val classDeclaration = method.parent as? ADClassDeclaration ?: return null
    val parameters = method.parameters.parameterList.map { parameter -> parameter.text }

    val clazz = classDeclaration.resolvePsiClass() ?: return null
    val methods = clazz.findMethodsByName(psi.text, false)

    return methods.firstOrNull { method -> parametersMatch(method.parameters, parameters) }
  }
}