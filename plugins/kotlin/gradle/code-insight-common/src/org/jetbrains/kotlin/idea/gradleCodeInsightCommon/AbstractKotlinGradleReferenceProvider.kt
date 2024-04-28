// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.model.psi.ImplicitReferenceProvider
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.calls.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

abstract class AbstractKotlinGradleReferenceProvider: ImplicitReferenceProvider {
    companion object {
        @JvmStatic
        protected val GRADLE_DSL_PACKAGE: FqName = FqName("org.gradle.kotlin.dsl")
    }

    protected fun getTextFromLiteralEntry(element: PsiElement?) : String? {
        return (element as? KtLiteralStringTemplateEntry)
            ?.takeIf { it.containingKtFile.isScript() }
            ?.text
    }
    
    protected fun analyzeSurroundingCallExpression(element: PsiElement?) : CallableId? {
        val callExpression = element?.getParentOfType<KtCallExpression>(true, KtDeclarationWithBody::class.java) ?: return null
        return analyze(callExpression) {
            val singleFunctionCallOrNull = callExpression.resolveCall()?.singleFunctionCallOrNull()
            singleFunctionCallOrNull?.symbol?.callableIdIfNonLocal
        }
    }
}