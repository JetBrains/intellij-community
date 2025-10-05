// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

private val log = Logger.getInstance("kotlin inlays")

internal inline fun collectInlaysWithErrorsLogging(forElement: PsiElement, action: () -> Unit) {
    try {
        action()
    } catch (e: ProcessCanceledException) {
        throw e
    } catch (e: IndexNotReadyException) {
        throw e
    } catch (e: Exception) {
        log.error(
            KotlinExceptionWithAttachments("Unable to provide inlay hint for $forElement", e)
                .withPsiAttachment("element", forElement)
        )
    }
}