// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.base.fe10.analysis

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.analysis.KotlinSafeAnalysisWrapper
import org.jetbrains.kotlin.idea.util.actionUnderSafeAnalyzeBlock

private class Fe10KotlinSafeAnalysisWrapper : KotlinSafeAnalysisWrapper {
    override fun <T> runSafely(context: PsiElement, body: () -> T, fallback: () -> T): T {
        return context.actionUnderSafeAnalyzeBlock(body, fallback)
    }
}