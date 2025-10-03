// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.threadingModelHelper

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod


interface KtCallResolver {

  fun resolveCallees(method: PsiMethod): List<PsiMethod>

  fun resolveReturnPsiClass(method: PsiMethod): PsiClass?
}
