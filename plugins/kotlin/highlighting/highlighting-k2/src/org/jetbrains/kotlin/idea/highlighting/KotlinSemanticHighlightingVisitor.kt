// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighting

import com.intellij.codeHighlighting.TextEditorHighlightingPass
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
 * A highlight visitor which generates a semantic INFORMATION-level highlightings (e.g., for smartcasts) and adds them to the [holder]
 */
class KotlinSemanticHighlightingVisitor : HighlightVisitor {
    private lateinit var holder: HighlightInfoHolder
    private lateinit var analyzers: Array<KotlinSemanticAnalyzer>
    private lateinit var kotlinRefsHolder: KotlinRefsHolder

    override fun suitableForFile(file: PsiFile): Boolean {
        return file is KtFile && !file.isCompiled
    }

    override fun analyze(ktFile: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        this.holder = holder
        kotlinRefsHolder = KotlinRefsHolder()
        analyze(ktFile as KtElement) {
            analyzers = createSemanticAnalyzers(kotlinRefsHolder, holder)
            action.run()
        }
        return true
    }

    context(KtAnalysisSession)
    private fun createSemanticAnalyzers(refsHolder: KotlinRefsHolder, holder: HighlightInfoHolder): Array<KotlinSemanticAnalyzer> = arrayOf(
      TypeHighlighter(refsHolder, holder),
      FunctionCallHighlighter(refsHolder, holder),
      ExpressionsSmartcastHighlighter(holder),
      VariableReferenceHighlighter(refsHolder, holder),
      DslHighlighter(holder),
    )

    override fun visit(element: PsiElement) {
        analyzers.forEach { analyzer ->
            element.accept(analyzer)
        }
        if (element is KtFile) {
            KotlinUnusedHighlightingVisitor(element, kotlinRefsHolder).collectHighlights(holder)
        }
    }

    override fun clone(): HighlightVisitor {
        return KotlinSemanticHighlightingVisitor()
    }
}
