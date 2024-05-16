// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.name.Name
import org.jetbrains.plugins.gradle.service.resolve.GradleProjectReference

private const val GRADLE_SEPARATOR = ":"
private val GRADLE_DSL_PROJECT: Name = Name.identifier("project")

class KotlinGradleProjectReferenceProvider: AbstractKotlinGradleReferenceProvider() {
    override fun getImplicitReference(
        element: PsiElement,
        offsetInElement: Int
    ): PsiSymbolReference? {
        val text = getTextFromLiteralEntry(element.parent)
            ?.takeIf { it.startsWith(GRADLE_SEPARATOR) } ?: return null
        val callableId = analyzeSurroundingCallExpression(element.parent) ?: return null

        if (callableId.packageName != GRADLE_DSL_PACKAGE || callableId.callableName != GRADLE_DSL_PROJECT) return null

        val length = element.textRange.length
        return if (text == GRADLE_SEPARATOR) {
            // in this special case we want the root project reference to span over the colon symbol
            GradleProjectReference(element, TextRange(0, length), emptyList())
        } else {
            GradleProjectReference(element, TextRange(1, length), text.substring(1).split(GRADLE_SEPARATOR))
        }
    }
}