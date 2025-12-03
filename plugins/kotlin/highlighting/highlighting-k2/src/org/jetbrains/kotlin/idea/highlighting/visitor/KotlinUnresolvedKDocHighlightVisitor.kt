// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.visitor

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName

/**
 * This visitor works in conjunction with 
 * [org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.KDocUnresolvedReferenceQuickFixProvider].
 */
internal class KotlinUnresolvedKDocHighlightVisitor : HighlightVisitor {
    private var _holder: HighlightInfoHolder? = null
    private val holder: HighlightInfoHolder get() = _holder!!

    override fun suitableForFile(psiFile: PsiFile): Boolean {
        return KotlinDiagnosticHighlightVisitor.shouldHighlightDiagnostics(psiFile)
    }

    override fun visit(element: PsiElement) {
        if (element !is KDocName) return
        val reference = element.mainReference
        if (reference.multiResolve(incompleteCode = false).isNotEmpty()) return
        val builder = HighlightInfo
            .newHighlightInfo(HighlightInfoType.WARNING)
            .range(reference.absoluteRange)
            .descriptionAndTooltip(ProblemsHolder.unresolvedReferenceMessage(reference))
        UnresolvedReferenceQuickFixProvider.registerUnresolvedReferenceLazyQuickFixes(reference, builder)
        holder.add(builder.create())
    }

    override fun analyze(
        psiFile: PsiFile,
        updateWholeFile: Boolean,
        holder: HighlightInfoHolder,
        action: Runnable
    ): Boolean {
        this._holder = holder
        try {
            action.run()
        } finally {
            this._holder = null
        }

        return true
    }

    override fun clone(): HighlightVisitor {
        return KotlinUnresolvedKDocHighlightVisitor()
    }
}