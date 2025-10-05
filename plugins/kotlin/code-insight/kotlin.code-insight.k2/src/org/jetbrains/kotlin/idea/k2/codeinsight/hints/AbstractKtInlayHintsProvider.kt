// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractKtInlayHintsProvider : InlayHintsProvider {

    open fun shouldShowForFile(ktFile: KtFile, project: Project): Boolean = true

    final override fun createCollector(
        file: PsiFile,
        editor: Editor
    ): InlayHintsCollector? {
        val project = editor.project ?: file.project
        if (project.isDefault || file !is KtFile) return null
        if (!shouldShowForFile(file, project)) return null

        return object : SharedBypassCollector {
            override fun collectFromElement(
                element: PsiElement,
                sink: InlayTreeSink
            ) {
                collectInlaysWithErrorsLogging(element) {
                    this@AbstractKtInlayHintsProvider.collectFromElement(element, sink)
                }
            }
        }
    }

    protected abstract fun collectFromElement(element: PsiElement, sink: InlayTreeSink)
}