// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.visitor

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinKdocQuickfixProvider
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import java.util.function.Consumer

internal class KotlinUnresolvedKDocHighlightVisitor : HighlightVisitor {
    private var _holder: HighlightInfoHolder? = null
    private val holder: HighlightInfoHolder get() = _holder!!

    override fun suitableForFile(psiFile: PsiFile): Boolean {
        return KotlinDiagnosticHighlightVisitor.shouldHighlightDiagnostics(psiFile)
    }

    override fun visit(element: PsiElement) {
        if (element !is KDocName) return
        val reference = element.mainReference
        if (reference.resolve() != null) return
        val builder = HighlightInfo
            .newHighlightInfo(HighlightInfoType.WARNING)
            .range(reference.absoluteRange)
            .descriptionAndTooltip(ProblemsHolder.unresolvedReferenceMessage(reference))
        builder.registerLazyFixes(LazyQuickfixProvider(element.createSmartPointer()))
        holder.add(builder.create())
    }

    private class LazyQuickfixProvider(private val pointer: SmartPsiElementPointer<KDocName>) : Consumer<QuickFixActionRegistrar> {
        override fun accept(registrar: QuickFixActionRegistrar) {
            val element = pointer.element ?: return
            val fixes = KotlinKdocQuickfixProvider.getKdocQuickFixesFor(element)
            for (fix in fixes) {
                registrar.register(fix)
            }
        }
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