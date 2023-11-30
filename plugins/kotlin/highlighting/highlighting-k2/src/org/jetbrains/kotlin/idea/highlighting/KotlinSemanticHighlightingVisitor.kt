// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.highlighting.highlighters.*
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * A highlight visitor which generates a semantic INFORMATION-level highlightings (e.g., for smart-casts) and adds them to the [HighlightInfoHolder]
 */
class KotlinSemanticHighlightingVisitor : HighlightVisitor {
    private lateinit var analyzers: Array<KtVisitorVoid>

    override fun suitableForFile(file: PsiFile): Boolean {
        return file is KtFile && !file.isCompiled
    }

    override fun analyze(ktFile: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        analyze(ktFile as KtElement) {
            analyzers = createSemanticAnalyzers(holder)
            action.run()
            KotlinUnusedHighlightingVisitor(ktFile as KtFile, holder).collectHighlights(holder)
        }
        return true
    }

    context(KtAnalysisSession)
    private fun createSemanticAnalyzers(holder: HighlightInfoHolder): Array<KtVisitorVoid> = arrayOf(
      TypeHighlighter(holder),
      FunctionCallHighlighter(holder),
      ExpressionsSmartcastHighlighter(holder),
      VariableReferenceHighlighter(holder),
      DslHighlighter(holder),
    )

    override fun visit(element: PsiElement) {
        analyzers.forEach { analyzer ->
            element.accept(analyzer)
        }
    }

    override fun clone(): HighlightVisitor {
        return KotlinSemanticHighlightingVisitor()
    }
}
