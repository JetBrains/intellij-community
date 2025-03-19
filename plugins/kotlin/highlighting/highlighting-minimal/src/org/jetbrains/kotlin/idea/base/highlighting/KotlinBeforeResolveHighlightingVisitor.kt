// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.highlighting.BeforeResolveHighlightingExtension.Companion.EP_NAME
import org.jetbrains.kotlin.psi.KtCommonFile

@ApiStatus.Internal
class KotlinBeforeResolveHighlightingVisitor: HighlightVisitor, DumbAware {
    private var visitors: List<PsiElementVisitor>? = null

    override fun suitableForFile(file: PsiFile): Boolean =
        @Suppress("DEPRECATION")
        file is KtCommonFile

    override fun visit(element: PsiElement) {
        visitors?.forEach(element::accept)
    }

    override fun analyze(
        file: PsiFile,
        updateWholeFile: Boolean,
        holder: HighlightInfoHolder,
        action: Runnable
    ): Boolean {
        try {
            visitors = EP_NAME.extensionList.map { it.createVisitor(holder) }
            action.run()
        } finally {
          visitors = null
        }
        return true
    }

    override fun clone(): HighlightVisitor = KotlinBeforeResolveHighlightingVisitor()
}