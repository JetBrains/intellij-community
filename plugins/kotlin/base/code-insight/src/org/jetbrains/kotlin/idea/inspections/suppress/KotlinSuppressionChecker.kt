// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.suppress

import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement

interface KotlinSuppressionChecker {
    companion object {
        @JvmStatic
        fun getInstance(): KotlinSuppressionChecker = service()
    }

    fun isSuppressedFor(element: PsiElement, toolId: String): Boolean
}