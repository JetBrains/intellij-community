// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.analysis

import com.intellij.openapi.components.serviceOrNull
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface KotlinSafeAnalysisWrapper {
    fun <T> runSafely(context: PsiElement, body: () -> T, fallback: () -> T): T

    companion object {
        fun <T> runSafely(context: PsiElement, body: () -> T, fallback: () -> T): T {
            val wrapper = serviceOrNull<KotlinSafeAnalysisWrapper>() ?: return body()
            return wrapper.runSafely(context, body, fallback)
        }
    }
}