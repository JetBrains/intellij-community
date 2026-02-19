// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.apiDump.lang.psi

import com.intellij.psi.PsiFile

internal interface ADFile : PsiFile, ADPsiElement {
  val classDeclarations: List<ADClassDeclaration>
}