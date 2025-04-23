// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.visitor

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.analysis.injectionRequiresOnlyEssentialHighlighting
import org.jetbrains.kotlin.idea.base.analysis.isInjectedFileShouldBeAnalyzed
import org.jetbrains.kotlin.idea.highlighting.KotlinUnusedHighlightingProcessor
import org.jetbrains.kotlin.psi.KtFile

internal class KotlinUnusedDeclarationHighlightingVisitor : HighlightVisitor {
    override fun suitableForFile(file: PsiFile): Boolean {
        if (file !is KtFile || file.isCompiled) return false

        val highlightingLevelManager = HighlightingLevelManager.getInstance(file.project)
        if (!highlightingLevelManager.shouldInspect(file)) return false

        val viewProvider = file.viewProvider
        val isInjection = InjectedLanguageManager.getInstance(file.project).isInjectedViewProvider(viewProvider)
        if (isInjection && (!viewProvider.isInjectedFileShouldBeAnalyzed || file.injectionRequiresOnlyEssentialHighlighting)) {
            // do not highlight unused declarations in injected code
            return false
        }

        val highlightingManager = HighlightingLevelManager.getInstance(file.project)
        return !highlightingManager.runEssentialHighlightingOnly(file)
    }

    override fun visit(element: PsiElement) {}

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        if (file !is KtFile) return true

        KotlinUnusedHighlightingProcessor(file).collectHighlights(holder)

        return true
    }

    override fun clone(): HighlightVisitor = KotlinUnusedDeclarationHighlightingVisitor()
}