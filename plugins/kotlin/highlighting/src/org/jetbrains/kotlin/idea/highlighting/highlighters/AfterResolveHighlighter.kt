// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.highlighters

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.psi.KtElement

internal abstract class AfterResolveHighlighter(protected val project: Project) {

    context(KtAnalysisSession)
    abstract fun highlight(element: KtElement) : List<HighlightInfo.Builder>

    companion object {
        fun createHighlighters(project: Project): List<AfterResolveHighlighter> = listOf(
          TypeHighlighter(project),
          FunctionCallHighlighter(project),
          ExpressionsSmartcastHighlighter(project),
          VariableReferenceHighlighter(project),
          DslHighlighter(project),
        )
    }
}