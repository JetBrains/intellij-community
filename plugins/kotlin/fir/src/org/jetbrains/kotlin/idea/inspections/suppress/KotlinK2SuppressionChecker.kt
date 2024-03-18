// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections.suppress

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.psi.KtAnnotated

class KotlinK2SuppressionChecker: KotlinSuppressionChecker {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        return element.parentsOfType<KtAnnotated>().any { parent ->
            val suppressedTools = KotlinPsiHeuristics.findSuppressedEntities(parent)?.map(String::lowercase)
            suppressedTools != null && toolId.lowercase() in suppressedTools
        }
    }
}