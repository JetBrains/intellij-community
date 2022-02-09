// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.highlighter

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.fir.highlighter.visitors.FirAfterResolveHighlightingVisitor
import org.jetbrains.kotlin.analysis.api.analyse
import org.jetbrains.kotlin.idea.highlighter.AbstractKotlinPsiChecker
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

class KotlinFirPsiChecker : AbstractKotlinPsiChecker() {
    override fun shouldHighlight(file: KtFile): Boolean {
        return true // todo
    }

    override fun annotateElement(element: PsiElement, containingFile: KtFile, holder: AnnotationHolder) {
        if (element !is KtElement) return
        if (isDispatchThread()) {
            throw ProcessCanceledException()
        }
        analyse(element) {
            FirAfterResolveHighlightingVisitor
                .createListOfVisitors(this, holder)
                .forEach(element::accept)
        }
    }
}