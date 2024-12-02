// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.visitor

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.highlighting.KotlinUnusedHighlightingProcessor
import org.jetbrains.kotlin.psi.KtFile

internal class KotlinUnusedDeclarationHighlightingVisitor : HighlightVisitor {
    override fun suitableForFile(file: PsiFile): Boolean = file is KtFile && !file.isCompiled

    override fun visit(element: PsiElement) {}

    override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
        val ktFile = file as? KtFile ?: return true
        val highlightingLevelManager = HighlightingLevelManager.getInstance(file.project)
        if (!highlightingLevelManager.shouldHighlight(ktFile)) return true

        analyze(ktFile) {
            KotlinUnusedHighlightingProcessor(file).collectHighlights(holder)
        }

        return true
    }

    override fun clone(): HighlightVisitor = KotlinUnusedDeclarationHighlightingVisitor()
}