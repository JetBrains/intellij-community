// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinExceptionUtils")
package org.jetbrains.kotlin.idea.util.application

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

fun KotlinExceptionWithAttachments.withPsiAttachment(name: String, element: PsiElement?): KotlinExceptionWithAttachments {
    kotlin.runCatching { element?.getElementTextWithContext() }.getOrNull()?.let { withAttachment(name, it) }
    return this
}