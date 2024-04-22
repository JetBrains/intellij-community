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
import org.jetbrains.plugins.gradle.service.resolve.GradlePluginReference

private val GRADLE_DSL_PACKAGE: FqName = FqName("org.gradle.kotlin.dsl")
private val GRADLE_DSL_ID: Name = Name.identifier("id")

class KotlinGradlePluginReferenceProvider: ImplicitReferenceProvider {
    override fun getImplicitReference(
      element: PsiElement,
      offsetInElement: Int
    ): PsiSymbolReference? {
        val parent = element.parent
        val callExpression = parent?.getParentOfType<KtCallExpression>(true, KtDeclarationWithBody::class.java) ?: return null

        val ktLiteralStringTemplateEntry = (parent as? KtLiteralStringTemplateEntry)
            ?.takeIf { it.containingKtFile.isScript() } ?: return null
        val value = ktLiteralStringTemplateEntry.text

        val callableId = analyze(callExpression) {
          val singleFunctionCallOrNull = callExpression.resolveCall()?.singleFunctionCallOrNull()
          singleFunctionCallOrNull?.symbol?.callableIdIfNonLocal ?: return null
        }

        if (callableId.packageName != GRADLE_DSL_PACKAGE || callableId.callableName != GRADLE_DSL_ID) return null

        val length = element.textRange.length
        return GradlePluginReference(element, TextRange(0, length), value)
    }
}