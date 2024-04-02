// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.model.psi.ImplicitReferenceProvider
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.plugins.gradle.service.resolve.GradleProjectReference

private const val GRADLE_SEPARATOR = ":"
private val GRADLE_DSL_PACKAGE: FqName = FqName("org.gradle.kotlin.dsl")
private val GRADLE_DSL_PROJECT: Name = Name.identifier("project")

class KotlinGradleProjectReferenceProvider: ImplicitReferenceProvider {
    override fun getImplicitReference(
        element: PsiElement,
        offsetInElement: Int
    ): PsiSymbolReference? {
        val parent = element.parent
        val callExpression = parent?.getParentOfType<KtCallExpression>(true, KtDeclarationWithBody::class.java) ?: return null

        val ktLiteralStringTemplateEntry = (parent as? KtLiteralStringTemplateEntry)
            ?.takeIf { it.containingKtFile.isScript() } ?: return null
        val value = ktLiteralStringTemplateEntry.text.takeIf { it.startsWith(GRADLE_SEPARATOR) } ?: return null

        val callableId = analyze(callExpression) {
            val singleFunctionCallOrNull = callExpression.resolveCall()?.singleFunctionCallOrNull()
            singleFunctionCallOrNull?.symbol?.callableIdIfNonLocal ?: return null
        }

        if (callableId.packageName != GRADLE_DSL_PACKAGE || callableId.callableName != GRADLE_DSL_PROJECT) return null

        val length = element.textRange.length
        return if (value == GRADLE_SEPARATOR) {
            // in this special case we want the root project reference to span over the colon symbol
            GradleProjectReference(element, TextRange(0, length), emptyList())
        } else {
            GradleProjectReference(element, TextRange(1, length), value.substring(1).split(GRADLE_SEPARATOR))
        }
    }
}