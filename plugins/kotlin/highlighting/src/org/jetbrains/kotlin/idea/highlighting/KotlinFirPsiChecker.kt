// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.highlighter.AbstractKotlinPsiChecker
import org.jetbrains.kotlin.idea.highlighting.highlighters.AfterResolveHighlighter
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
        val highlighters = AfterResolveHighlighter.createHighlighters(holder, containingFile.project)
        analyze(element) {
            for (highlighter in highlighters) {
                highlighter.highlight(element)
            }
        }
    }
}