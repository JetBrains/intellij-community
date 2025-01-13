// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ADPsiImplUtil")
@file:Suppress("EXTENSION_SHADOWED_BY_MEMBER")

package com.intellij.devkit.apiDump.lang.psi

import com.intellij.psi.PsiClass

internal fun ADClassDeclaration.resolvePsiClass(): PsiClass? {
  val typeReference = classHeader.typeReference
  val classRef = typeReference.references.lastOrNull() ?: return null
  return classRef.resolve() as? PsiClass
}
