// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.visitor

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.highlighting.analyzers.KotlinSemanticAnalyzer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * A highlight visitor which generates a semantic INFORMATION-level highlightings (e.g., for smart-casts) and adds them to the [HighlightInfoHolder]
 */
internal abstract class KotlinAbstractSemanticHighlightingVisitor : HighlightVisitor {
    private var analyzer: KtVisitorVoid? = null

    override fun suitableForFile(file: PsiFile): Boolean {
        if (file !is KtFile || file.isCompiled) return false

        val highlightingLevelManager = HighlightingLevelManager.getInstance(file.project)
        return highlightingLevelManager.shouldHighlight(file)
    }

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        val ktFile = file as? KtFile ?: return true

        analyze(ktFile) {
            try {
                analyzer = createSemanticAnalyzer(holder, this)
                action.run()
            } finally {
                // The analyzer must be cleared to avoid leakages
                analyzer = null
            }
        }

        return true
    }

    /**
     * @see KotlinSemanticAnalyzer
     */
    protected abstract fun createSemanticAnalyzer(holder: HighlightInfoHolder, session: KaSession): KotlinSemanticAnalyzer

    override fun visit(element: PsiElement) {
        val visitor = analyzer ?: error("Analyzer for ${this::class.simpleName} is not initialized")
        element.accept(visitor)
    }

    abstract override fun clone(): KotlinAbstractSemanticHighlightingVisitor
}