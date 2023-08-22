// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.idea.inspections.suppress.KotlinSuppressionChecker

class KotlinK1SuppressionChecker: KotlinSuppressionChecker {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean = KotlinCacheService.getInstance(element.project)
        .getSuppressionCache()
        .isSuppressed(element, element.containingFile, toolId, Severity.WARNING)
}