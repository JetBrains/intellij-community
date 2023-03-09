// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggestionProvider
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.fe10.codeInsight.newDeclaration.Fe10KotlinNewDeclarationNameValidator
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.intentions.canBeReplacedWithInvokeCall
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.resolve.calls.util.getParameterForArgument
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.isNullabilityMismatch

class WrapWithSafeLetCallFix(
    expression: KtExpression,
    nullableExpression: KtExpression
) : KotlinQuickFixAction<KtExpression>(expression), HighPriorityAction {
    private val nullableExpressionPointer = nullableExpression.createSmartPointer()

    override fun getFamilyName() = text

    override fun getText() = KotlinBundle.message("wrap.with.let.call")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val nullableExpression = nullableExpressionPointer.element ?: return
        val qualifiedExpression = element.getQualifiedExpressionForSelector()
        val receiverExpression = qualifiedExpression?.receiverExpression
        val canBeReplacedWithInvokeCall = (nullableExpression.parent as? KtCallExpression)?.canBeReplacedWithInvokeCall() == true

        val psiFactory = KtPsiFactory(project)
        val nullableText = if (receiverExpression != null && canBeReplacedWithInvokeCall) {
            "${receiverExpression.text}${qualifiedExpression.operationSign.value}${nullableExpression.text}"
        } else {
            nullableExpression.text
        }
        val validator = Fe10KotlinNewDeclarationNameValidator(
            element,
            nullableExpression,
            KotlinNameSuggestionProvider.ValidatorTarget.PARAMETER
        )
        val name = Fe10KotlinNameSuggester.suggestNameByName("it", validator)

        nullableExpression.replace(psiFactory.createExpression(name))
        val underLetExpression = when {
            receiverExpression != null && !canBeReplacedWithInvokeCall ->
                psiFactory.createExpressionByPattern("$0.$1", receiverExpression, element)
            else -> element
        }
        val wrapped = when (name) {
            "it" -> psiFactory.createExpressionByPattern("($0)?.let { $1 }", nullableText, underLetExpression)
            else -> psiFactory.createExpressionByPattern("($0)?.let { $1 -> $2 }", nullableText, name, underLetExpression)
        }
        val replaced = (qualifiedExpression ?: element).replace(wrapped) as KtSafeQualifiedExpression
        val receiver = replaced.receiverExpression
        if (receiver is KtParenthesizedExpression && KtPsiUtil.areParenthesesUseless(receiver)) {
            receiver.replace(KtPsiUtil.safeDeparenthesize(receiver))
        }
    }

    object UnsafeFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val element = diagnostic.psiElement
            if (element is KtNameReferenceExpression) {
                val resolvedCall = element.resolveToCall()
                if (resolvedCall?.call?.callType != Call.CallType.INVOKE) return null
            }
            val expression = element.getStrictParentOfType<KtExpression>() ?: return null
            val (targetExpression, nullableExpression) = if (expression is KtQualifiedExpression) {
                val argument = expression.parent as? KtValueArgument ?: return null
                val call = argument.getStrictParentOfType<KtCallExpression>() ?: return null
                val parameter = call.resolveToCall()?.getParameterForArgument(argument) ?: return null
                if (parameter.type.isNullable()) return null
                val targetExpression = call.getLastParentOfTypeInRow<KtQualifiedExpression>() ?: call
                targetExpression to expression.receiverExpression
            } else {
                val nullableExpression = (element.parent as? KtCallExpression)?.calleeExpression ?: return null
                expression to nullableExpression
            }
            return WrapWithSafeLetCallFix(targetExpression, nullableExpression)
        }
    }

    object TypeMismatchFactory : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val element = diagnostic.psiElement as? KtExpression ?: return null
            val argument = element.parent as? KtValueArgument ?: return null
            val call = argument.getParentOfType<KtCallExpression>(true) ?: return null

            val expectedType: KotlinType
            val actualType: KotlinType
            when (diagnostic.factory) {
                Errors.TYPE_MISMATCH -> {
                    val diagnosticWithParameters = Errors.TYPE_MISMATCH.cast(diagnostic)
                    expectedType = diagnosticWithParameters.a
                    actualType = diagnosticWithParameters.b
                }
                Errors.TYPE_MISMATCH_WARNING -> {
                    val diagnosticWithParameters = Errors.TYPE_MISMATCH_WARNING.cast(diagnostic)
                    expectedType = diagnosticWithParameters.a
                    actualType = diagnosticWithParameters.b
                }
                ErrorsJvm.NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS -> {
                    val diagnosticWithParameters = ErrorsJvm.NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS.cast(diagnostic)
                    expectedType = diagnosticWithParameters.a
                    actualType = diagnosticWithParameters.b
                }
                else -> return null
            }

            if (element.isNull() || !isNullabilityMismatch(expected = expectedType, actual = actualType)) return null
            return WrapWithSafeLetCallFix(call.getLastParentOfTypeInRow<KtQualifiedExpression>() ?: call, element)
        }
    }
}