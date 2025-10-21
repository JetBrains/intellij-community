// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.diagnostics.DiagnosticFactoryWithPsiElement
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeAsReplacement
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.isAnnotatedDeep
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class ReplaceTypeArgumentWithUnderscoreIntention : SelfTargetingRangeIntention<KtTypeProjection>(
    KtTypeProjection::class.java, KotlinBundle.messagePointer("replace.with.underscore")
), LowPriorityAction {
    override fun applicabilityRange(element: KtTypeProjection): TextRange? {
        if (!element.languageVersionSettings.supportsFeature(LanguageFeature.PartiallySpecifiedTypeArguments)) return null
        if (element.typeReference?.isPlaceholder == true) return null
        val typeArgumentList = element.parent as? KtTypeArgumentList ?: return null
        val callExpression = typeArgumentList.parent as? KtCallExpression ?: return null
        if (typeArgumentList.arguments.any { it.typeReference?.isAnnotatedDeep() == true }) return null
        if (!isTypePreservedAfterReplacement(element, callExpression)) return null
        return element.textRange
    }

    override fun applyTo(element: KtTypeProjection, editor: Editor?) {
        replaceWithUnderscore(element)
    }

    private fun isTypePreservedAfterReplacement(element: KtTypeProjection, callExpression: KtCallExpression): Boolean {
        val bindingContext = callExpression.analyze(BodyResolveMode.PARTIAL)
        val (contextExpression, _) = RemoveExplicitTypeArgumentsIntention.findContextToAnalyze(callExpression, bindingContext)

        val key = Key<Unit>("ReplaceTypeArgumentWithUnderscoreIntention")
        callExpression.putCopyableUserData(key, Unit)
        val expressionToAnalyze = contextExpression.copied()
        callExpression.putCopyableUserData(key, null)
        val newCallExpression =
            expressionToAnalyze.findDescendantOfType<KtCallExpression> { it.getCopyableUserData(key) != null } ?: return false

        val newTypeArgumentList = newCallExpression.typeArgumentList ?: return false
        val indexOfReplacedType = callExpression.typeArguments.indexOf(element)
        val copiedTypeProjection = newTypeArgumentList.arguments.getOrNull(indexOfReplacedType) ?: return false
        replaceWithUnderscore(copiedTypeProjection)

        val newBindingContext = expressionToAnalyze.analyzeAsReplacement(callExpression, bindingContext)
        if (newBindingContext.diagnostics.any { it.factory in POSSIBLE_DIAGNOSTIC_ERRORS }) return false

        val currentTypeArguments = callExpression.getResolvedCall(bindingContext)?.typeArguments?.map { it.value } ?: return false
        val updatedTypeArguments = newCallExpression.getResolvedCall(newBindingContext)?.typeArguments?.map { it.value } ?: return false
        return currentTypeArguments == updatedTypeArguments
    }

    private fun replaceWithUnderscore(element: KtTypeProjection) {
        val newTypeProjection = KtPsiFactory(element.project).createTypeArgument("_")
        element.replace(newTypeProjection)
    }
}

private val POSSIBLE_DIAGNOSTIC_ERRORS: Set<DiagnosticFactoryWithPsiElement<*, *>> =
    setOf(
        Errors.NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER,
        Errors.INFERRED_INTO_DECLARED_UPPER_BOUNDS,
        Errors.UNRESOLVED_REFERENCE,
        Errors.BUILDER_INFERENCE_STUB_RECEIVER
    )
