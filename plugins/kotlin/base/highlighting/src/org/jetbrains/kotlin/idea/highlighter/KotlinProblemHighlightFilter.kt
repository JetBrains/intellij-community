// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.highlighting.shouldHighlightFile
import org.jetbrains.kotlin.psi.KtFile

class KotlinProblemHighlightFilter : ProblemHighlightFilter() {
    override fun shouldHighlight(file: PsiFile): Boolean = when (file) {
        is KtFile -> file.shouldHighlightFile()
        else -> true
    }

    override fun shouldProcessInBatch(file: PsiFile) = when (file) {
        is KtFile -> !file.isCompiled && file.shouldHighlightFile()
        else -> true
    }
}
