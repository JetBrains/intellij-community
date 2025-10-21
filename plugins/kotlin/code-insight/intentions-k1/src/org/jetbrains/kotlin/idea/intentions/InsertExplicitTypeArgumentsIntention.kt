// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors.DEFINITELY_NON_NULLABLE_AS_REIFIED
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.quickfix.KotlinSingleIntentionActionFactory
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.inference.CapturedType
import org.jetbrains.kotlin.resolve.calls.tower.NewResolvedCallImpl
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.DefinitelyNotNullType
import org.jetbrains.kotlin.types.checker.NewCapturedType
import org.jetbrains.kotlin.types.error.ErrorUtils

class InsertExplicitTypeArgumentsIntention : SelfTargetingRangeIntention<KtCallExpression>(
    KtCallExpression::class.java,
    KotlinBundle.messagePointer("add.explicit.type.arguments")
), LowPriorityAction {
    override fun applicabilityRange(element: KtCallExpression): TextRange? =
        if (isApplicableTo(element)) element.calleeExpression?.textRange else null

    override fun applyTo(element: KtCallExpression, editor: Editor?): Unit = applyTo(element)

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction = InsertExplicitTypeArgumentsIntention()

        fun isApplicableTo(element: KtCallElement, bindingContext: BindingContext = element.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)): Boolean {
            if (element.typeArguments.isNotEmpty()) return false
            if (element.calleeExpression == null) return false

            val resolvedCall = element.getResolvedCall(bindingContext) ?: return false
            val typeArgs = resolvedCall.typeArguments
            val valueParameters = resolvedCall.resultingDescriptor.valueParameters
            if (resolvedCall is NewResolvedCallImpl<*> && valueParameters.any { ErrorUtils.containsErrorType(it.type) }) return false

            /** Can't use definitely-non-nullable type as reified type argument, see [DEFINITELY_NON_NULLABLE_AS_REIFIED] */
            if (valueParameters.any { it.type is DefinitelyNotNullType && (it.original.type.constructor.declarationDescriptor as? TypeParameterDescriptor)?.isReified == true })
                return false

            return typeArgs.isNotEmpty() && typeArgs.values.none { ErrorUtils.containsErrorType(it) || it is CapturedType || it is NewCapturedType }
        }

        fun applyTo(element: KtCallElement, argumentList: KtTypeArgumentList, shortenReferences: Boolean = true) {
            val callee = element.calleeExpression ?: return
            val newArgumentList = element.addAfter(argumentList, callee) as KtTypeArgumentList
            if (shortenReferences) {
                ShortenReferences.DEFAULT.process(newArgumentList)
            }
        }

        fun applyTo(element: KtCallElement, shortenReferences: Boolean = true) {
            val argumentList = createTypeArguments(element, element.analyze()) ?: return
            applyTo(element, argumentList, shortenReferences)
        }

        fun createTypeArguments(element: KtCallElement, bindingContext: BindingContext): KtTypeArgumentList? {
            val resolvedCall = element.getResolvedCall(bindingContext) ?: return null

            val args = resolvedCall.typeArguments
            val types = resolvedCall.candidateDescriptor.typeParameters

            val text = types.joinToString(", ", "<", ">") {
                IdeDescriptorRenderers.SOURCE_CODE.renderType(args.getValue(it))
            }

            return KtPsiFactory(element.project).createTypeArguments(text)
        }
    }
}
