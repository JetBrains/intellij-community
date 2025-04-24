// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.references

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiElement

class PsiSourceResolveResult(element: PsiElement, val owners: List<PsiClass>): PsiElementResolveResult(element) {
}