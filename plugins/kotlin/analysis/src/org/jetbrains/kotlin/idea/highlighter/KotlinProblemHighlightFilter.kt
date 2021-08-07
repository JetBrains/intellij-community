// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinFileType

class KotlinProblemHighlightFilter : ProblemHighlightFilter() {

    override fun shouldHighlight(psiFile: PsiFile): Boolean =
        psiFile.fileType != KotlinFileType.INSTANCE || KotlinHighlightingUtil.shouldHighlight(psiFile)
}
