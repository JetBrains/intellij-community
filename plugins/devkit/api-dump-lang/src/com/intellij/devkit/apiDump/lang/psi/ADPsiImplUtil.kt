// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ADPsiImplUtil")
@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package com.intellij.devkit.apiDump.lang.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet

internal fun ADClassDeclaration.resolvePsiClass(): PsiClass? {
  val typeReference = classHeader.typeReference
  val classRef = typeReference.references.lastOrNull() ?: return null
  return classRef.resolve() as? PsiClass
}

internal fun ADClassDeclaration.getNavigationElement(): PsiElement =
  classHeader.typeReference.getIdentifierList().lastOrNull() ?: this

internal fun ADMethod.getNavigationElement(): PsiElement =
  methodReference

internal fun ADField.getNavigationElement(): PsiElement =
  fieldReference

internal fun ADConstructor.getNavigationElement(): PsiElement =
  constructorReference

private val identifierSet = TokenSet.create(ADElementTypes.IDENTIFIER)

internal fun ADTypeReference.getIdentifierList(): List<PsiElement> =
  node.getChildren(identifierSet).map { it.psi }