// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.serviceOrNull
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface KDocUnresolvedLinkQuickFixFactory {
    companion object {
        @JvmStatic
        fun getInstance(): KDocUnresolvedLinkQuickFixFactory? = serviceOrNull()
    }

    fun createQuickFix(element: PsiElement): IntentionAction?
}
