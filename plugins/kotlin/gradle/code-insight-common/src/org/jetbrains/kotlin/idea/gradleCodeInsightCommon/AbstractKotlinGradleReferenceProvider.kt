// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleCodeInsightCommon

import com.intellij.model.psi.ImplicitReferenceProvider
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

private const val GRADLE_KTS_EXTENSION = ".gradle.kts"

abstract class AbstractKotlinGradleReferenceProvider: ImplicitReferenceProvider {
    companion object {
        @JvmStatic
        protected val GRADLE_DSL_PACKAGE: FqName = FqName("org.gradle.kotlin.dsl")
        @JvmStatic
        protected val GRADLE_DSL_SUPPORT_DELEGATES_PACKAGE: FqName = FqName("org.gradle.kotlin.dsl.support.delegates")
        @JvmStatic
        protected val KGP_PACKAGE: FqName = FqName("org.jetbrains.kotlin.gradle.plugin")
    }

    final override fun getImplicitReference(element: PsiElement, offsetInElement: Int): PsiSymbolReference? {
        val file = element.containingFile ?: return null
        if (!file.name.endsWith(GRADLE_KTS_EXTENSION)) return null

        return getGradleImplicitReference(element, offsetInElement)
    }

    protected abstract fun getGradleImplicitReference(element: PsiElement, offsetInElement: Int): PsiSymbolReference?

    protected fun getTextFromLiteralEntry(element: PsiElement?) : String? {
        return (element as? KtLiteralStringTemplateEntry)
            ?.takeIf { it.containingKtFile.isScript() }
            ?.text
    }
    
    @OptIn(KaAllowAnalysisOnEdt::class)
    protected fun analyzeSurroundingCallExpression(element: PsiElement?) : CallableId? {
        val callExpression = element?.getParentOfType<KtCallExpression>(true, KtDeclarationWithBody::class.java) ?: return null
        return allowAnalysisOnEdt {
            analyze(callExpression) {
                callExpression.resolveToCall()?.singleFunctionCallOrNull()?.symbol?.callableId
            }
        }
    }
}
