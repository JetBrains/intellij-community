// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.psi.PsiMethod

interface LockReqAnalyzer {

  fun analyzeMethod(method: PsiMethod): AnalysisResult
}