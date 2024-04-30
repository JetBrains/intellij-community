// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.name.Name
import org.jetbrains.plugins.gradle.service.resolve.GradlePluginReference

private val GRADLE_DSL_ID: Name = Name.identifier("id")

class KotlinGradlePluginReferenceProvider : AbstractKotlinGradleReferenceProvider() {
    override fun getImplicitReference(
        element: PsiElement,
        offsetInElement: Int
    ): PsiSymbolReference? {
        val text = getTextFromLiteralEntry(element.parent) ?: return null
        val callableId = analyzeSurroundingCallExpression(element.parent) ?: return null
        if (callableId.packageName != GRADLE_DSL_PACKAGE || callableId.callableName != GRADLE_DSL_ID) return null

        val length = element.textRange.length
        return GradlePluginReference(element, TextRange(0, length), text)
    }
}