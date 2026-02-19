// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.psi.impl

import com.intellij.devkit.apiDump.lang.psi.ADClassDeclaration
import com.intellij.devkit.apiDump.lang.psi.ADConstructor
import com.intellij.devkit.apiDump.lang.psi.ADConstructorReference
import com.intellij.devkit.apiDump.lang.reference.ADMemberPsiReference
import com.intellij.devkit.apiDump.lang.reference.getReference
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.tree.IElementType

internal abstract class ADConstructorReferenceImpl(type: IElementType) : ADPsiElementImpl(type), ADConstructorReference {
  override fun getReference(): PsiReference? =
    getReference { ADConstructorPsiReference(this) }
}

private class ADConstructorPsiReference(psi: ADConstructorReference) : ADMemberPsiReference(psi) {
  override fun resolveRaw(): PsiElement? {
    val constructor = psi.parent as? ADConstructor ?: return null
    val classDeclaration = constructor.parent as? ADClassDeclaration ?: return null
    val parameters = constructor.parameters.parameterList.map { parameter -> parameter.text }

    val clazz = classDeclaration.resolvePsiClass() ?: return null
    val constructors = clazz.constructors

    return constructors.firstOrNull { constructor -> parametersMatch(constructor.parameters, parameters) }
  }
}