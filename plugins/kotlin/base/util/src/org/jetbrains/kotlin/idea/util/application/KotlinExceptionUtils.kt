// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinExceptionUtils")
package org.jetbrains.kotlin.idea.util.application

import com.intellij.diagnostic.AttachmentFactory
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

@Deprecated("use org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments#withPsiAttachment directly")
fun KotlinExceptionWithAttachments.withPsiAttachment(name: String, element: PsiElement?): KotlinExceptionWithAttachments {
    try {
        val text = element?.getElementTextWithContext()
        withAttachment(name, text)
    } catch (e: Throwable) {
        // Ignore
    }

    return this
}

@ApiStatus.Internal
fun attachmentByPsiFile(file: PsiFile?): Attachment? {
    if (file == null) {
        return null
    }

    val virtualFile = file.virtualFile
    if (virtualFile != null) {
        return AttachmentFactory.createAttachment(virtualFile)
    }

    return runReadAction {
        try {
            val name = file.name
            val text = file.text

            if (text != null) {
                return@runReadAction Attachment(name, text)
            }
        } catch (e: Throwable) {
            // Ignore
        }

        return@runReadAction null
    }
}

@ApiStatus.Internal
fun Collection<Attachment>.merge(): Attachment {
    val text = buildString {
        for (attachment in this@merge) {
            append("----- START ${attachment.path} -----\n")
            append(attachment.displayText)
            append("\n----- END ${attachment.path} -----\n\n")
        }
    }

    return Attachment("message.txt", text)
}