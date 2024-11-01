// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

abstract class AbstractKtInlayHintsProvider: InlayHintsProvider {
    @Suppress("SSBasedInspection")
    private val log = Logger.getInstance(this::class.java)

    final override fun createCollector(
        file: PsiFile,
        editor: Editor
    ): InlayHintsCollector? {
        val project = editor.project ?: file.project
        if (project.isDefault || file !is KtFile) return null

        return object : SharedBypassCollector {
            override fun collectFromElement(
                element: PsiElement,
                sink: InlayTreeSink
            ) {
                try {
                    this@AbstractKtInlayHintsProvider.collectFromElement(element, sink)
                } catch (e: ProcessCanceledException) {
                    throw e
                } catch (e: IndexNotReadyException) {
                    throw e
                } catch (e: Exception) {
                    log.error(KotlinExceptionWithAttachments("Unable to provide inlay hint for $element", e)
                        .withPsiAttachment("element", element))
                }
            }
        }
    }

    protected abstract fun collectFromElement(element: PsiElement, sink: InlayTreeSink)
}