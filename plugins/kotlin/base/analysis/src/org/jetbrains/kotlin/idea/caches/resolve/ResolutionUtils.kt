// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ResolutionUtils")
package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

@Deprecated("This function is deprecated and will be removed in the future.")
fun KtReferenceExpression.resolveMainReference(): PsiElement? =
    try {
        mainReference.resolve()
    } catch (e: Exception) {
        if (e is ControlFlowException) throw e
        throw KotlinExceptionWithAttachments("Unable to resolve reference", e)
            .withPsiAttachment("reference.txt", this)
            .withPsiAttachment("file.kt", containingFile)
    }