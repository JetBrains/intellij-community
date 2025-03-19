// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions.branchedTransformations

import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinNameSuggester
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatementOrThis
import org.jetbrains.kotlin.idea.base.psi.isNullExpression
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.caches.resolve.findModuleDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeInsight.IfThenTransformationData
import org.jetbrains.kotlin.idea.codeInsight.IfThenTransformationUtils.checkedExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.getLeftMostReceiverExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.replaceFirstReceiver
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isSimplifiableTo
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.refactoring.inline.KotlinInlinePropertyHandler
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable.K1IntroduceVariableHandler
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.resolve.dataFlowValueFactory
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValue
import org.jetbrains.kotlin.resolve.calls.util.getExplicitReceiverValue
import org.jetbrains.kotlin.resolve.calls.util.getImplicitReceiverValue
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.scopes.receivers.ImplicitReceiver
import org.jetbrains.kotlin.resolve.scopes.utils.findVariable
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.typeUtil.isSubtypeOf
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments
import org.jetbrains.kotlin.utils.addToStdlib.constant

fun KtExpression?.isTrivialStatementBody(): Boolean = when (this?.getSingleUnwrappedStatementOrThis()) {
    is KtIfExpression, is KtBlockExpression -> false
    else -> true
}

fun KtExpression?.isNullExpression(): Boolean = isNullExpression()

fun KtThrowExpression.throwsNullPointerExceptionWithNoArguments(): Boolean {
    val thrownExpression = this.thrownExpression as? KtCallExpression ?: return false

    val nameExpression = thrownExpression.calleeExpression as? KtNameReferenceExpression ?: return false
    val descriptor = nameExpression.resolveToCall()?.resultingDescriptor
    val declDescriptor = descriptor?.containingDeclaration ?: return false

    val exceptionName = DescriptorUtils.getFqName(declDescriptor).asString()
    return exceptionName in constant {
        setOf(
            "kotlin.KotlinNullPointerException",
            "kotlin.NullPointerException",
            "java.lang.NullPointerException"
        )
    } && thrownExpression.valueArguments.isEmpty()
}

fun KtExpression.anyArgumentEvaluatesTo(argument: KtExpression): Boolean {
    val callExpression = this as? KtCallExpression ?: return false
    val arguments = callExpression.valueArguments.map { it.getArgumentExpression() }
    return arguments.any { it?.isSimplifiableTo(argument) == true } && arguments.all { it is KtNameReferenceExpression }
}

fun KtExpression.convertToIfNotNullExpression(
    conditionLhs: KtExpression,
    thenClause: KtExpression,
    elseClause: KtExpression?
): KtIfExpression {
    val condition = KtPsiFactory(project).createExpressionByPattern("$0 != null", conditionLhs)
    return this.convertToIfStatement(condition, thenClause, elseClause)
}

fun KtExpression.convertToIfNullExpression(conditionLhs: KtExpression, thenClause: KtExpression): KtIfExpression {
    val condition = KtPsiFactory(project).createExpressionByPattern("$0 == null", conditionLhs)
    return this.convertToIfStatement(condition, thenClause)
}

fun KtExpression.convertToIfStatement(condition: KtExpression, thenClause: KtExpression, elseClause: KtExpression? = null): KtIfExpression =
    replaced(KtPsiFactory(project).createIf(condition, thenClause, elseClause))

fun KtIfExpression.introduceValueForCondition(occurrenceInThenClause: KtExpression, editor: Editor?) {
    val occurrenceInConditional = when (val condition = condition) {
        is KtBinaryExpression -> condition.left
        is KtIsExpression -> condition.leftHandSide
        else -> throw KotlinExceptionWithAttachments("Only binary / is expressions are supported here: ${condition?.let { it::class.java }}")
            .withPsiAttachment("condition", condition)
    }!!
    K1IntroduceVariableHandler.collectCandidateTargetContainersAndDoRefactoring(
        project,
        editor,
        occurrenceInConditional,
        false,
        listOf(occurrenceInConditional, occurrenceInThenClause),
        onNonInteractiveFinish = null,
    )
}

fun KtNameReferenceExpression.inlineIfDeclaredLocallyAndOnlyUsedOnce(editor: Editor?, withPrompt: Boolean) {
    val declaration = this.mainReference.resolve() as? KtProperty ?: return

    val enclosingElement = KtPsiUtil.getEnclosingElementForLocalDeclaration(declaration)
    val isLocal = enclosingElement != null
    if (!isLocal) return

    val scope = LocalSearchScope(enclosingElement!!)

    val references = ReferencesSearch.search(declaration, scope).findAll()
    if (references.size == 1) {
        if (!isUnitTestMode()) {
            invokeLater {
                val handler = KotlinInlinePropertyHandler(withPrompt)
                if (declaration.isValid && handler.canInlineElement(declaration)) {
                    TransactionGuard.getInstance().submitTransactionAndWait {
                        handler.inlineElement(this.project, editor, declaration)
                    }
                }
            }
        } else {
            KotlinInlinePropertyHandler(withPrompt).inlineElement(this.project, editor, declaration)
        }
    }
}

fun KtSafeQualifiedExpression.inlineReceiverIfApplicable(editor: Editor?, withPrompt: Boolean) {
    (this.receiverExpression as? KtNameReferenceExpression)?.inlineIfDeclaredLocallyAndOnlyUsedOnce(editor, withPrompt)
}

fun KtBinaryExpression.inlineLeftSideIfApplicable(editor: Editor?, withPrompt: Boolean) {
    (this.left as? KtNameReferenceExpression)?.inlineIfDeclaredLocallyAndOnlyUsedOnce(editor, withPrompt)
}

// I.e. stable val/var/receiver
// We exclude stable complex expressions here, because we don't do smartcasts on them (even though they are stable)
fun KtExpression.isStableSimpleExpression(context: BindingContext = this.safeAnalyzeNonSourceRootCode()): Boolean {
    val dataFlowValue = this.toDataFlowValue(context)
    return dataFlowValue?.isStable == true &&
            dataFlowValue.kind != DataFlowValue.Kind.STABLE_COMPLEX_EXPRESSION

}

fun elvisPattern(newLine: Boolean): String = if (newLine) "$0\n?: $1" else "$0 ?: $1"

private fun KtExpression.toDataFlowValue(context: BindingContext): DataFlowValue? {
    val expressionType = this.getType(context) ?: return null
    val dataFlowValueFactory = this.getResolutionFacade().dataFlowValueFactory
    return dataFlowValueFactory.createDataFlowValue(this, expressionType, context, findModuleDescriptor())
}

internal fun IfThenTransformationData.replacedBaseClause(factory: KtPsiFactory, context: BindingContext): KtExpression {
    val baseClause = baseClause
    val newReceiver = (condition as? KtIsExpression)?.let {
        factory.createExpressionByPattern(
            "$0 as? $1",
            it.leftHandSide,
            it.typeReference!!
        )
    }

    return if (baseClause.isSimplifiableTo(checkedExpression)) {
        if (condition is KtIsExpression) newReceiver!! else baseClause
    } else {
        when {
            condition is KtIsExpression -> {
                when {
                    baseClause is KtDotQualifiedExpression -> baseClause.replaceFirstReceiver(
                        factory, newReceiver!!, safeAccess = true
                    )

                    hasImplicitReceiverReplaceableBySafeCall(context) -> factory.createExpressionByPattern(
                        "$0?.$1",
                        newReceiver!!,
                        baseClause
                    ).insertSafeCalls(
                        factory
                    )

                    baseClause is KtCallExpression -> baseClause.replaceCallWithLet(newReceiver!!, checkedExpression, factory)
                    else -> error("Illegal state")
                }
            }

            hasImplicitReceiverReplaceableBySafeCall(context) -> factory.createExpressionByPattern(
                "$0?.$1",
                checkedExpression,
                baseClause
            ).insertSafeCalls(factory)

            baseClause is KtCallExpression -> {
                val callee = baseClause.calleeExpression
                if (callee != null && baseClause.isCallingInvokeFunction(context)) {
                    factory.createExpressionByPattern("$0?.invoke()", callee)
                } else {
                    baseClause.replaceCallWithLet(checkedExpression, checkedExpression, factory)
                }
            }

            else -> {
                var replaced = baseClause.insertSafeCalls(factory)
                if (replaced is KtQualifiedExpression) {
                    val call = replaced.callExpression
                    val callee = call?.calleeExpression
                    if (callee != null && callee.text != "invoke" && call.isCallingInvokeFunction(context)) {
                        replaced = factory.createExpressionByPattern("$0?.$1?.invoke()", replaced.receiverExpression, callee)
                    }
                }
                replaced
            }
        }
    }
}

internal fun KtExpression.isCallingInvokeFunction(context: BindingContext): Boolean {
    if (this !is KtCallExpression) return false
    val resolvedCall = getResolvedCall(context) ?: resolveToCall() ?: return false
    val descriptor = resolvedCall.resultingDescriptor as? SimpleFunctionDescriptor ?: return false
    return descriptor is FunctionInvokeDescriptor || descriptor.isOperator && descriptor.name.asString() == "invoke"
}

internal fun IfThenTransformationData.getImplicitReceiver(context: BindingContext): ImplicitReceiver? {
    val resolvedCall = baseClause.getResolvedCall(context) ?: return null
    if (resolvedCall.getExplicitReceiverValue() != null) return null
    return resolvedCall.getImplicitReceiverValue()
}

internal fun IfThenTransformationData.hasImplicitReceiverReplaceableBySafeCall(context: BindingContext): Boolean =
    checkedExpression is KtThisExpression && getImplicitReceiver(context) != null

private fun KtCallExpression.replaceCallWithLet(
    newReceiver: KtExpression,
    oldReceiver: KtExpression,
    factory: KtPsiFactory,
): KtExpression {
    val needExplicitParameter =
        valueArguments.any { it.getArgumentExpression()?.text == StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier }
    val parameterName = if (needExplicitParameter) {
        val scope = getResolutionScope()
        KotlinNameSuggester.suggestNameByName("it") { scope.findVariable(Name.identifier(it), NoLookupLocation.FROM_IDE) == null }
    } else {
        "it"
    }
    return factory.buildExpression {
        appendExpression(newReceiver)
        appendFixedText("?.let {")
        if (needExplicitParameter) appendFixedText(" $parameterName ->")
        appendExpression(calleeExpression)
        appendFixedText("(")
        valueArguments.forEachIndexed { index, arg ->
            if (index != 0) appendFixedText(", ")
            val argName = arg.getArgumentName()?.asName
            if (argName != null) {
                appendName(argName)
                appendFixedText(" = ")
            }
            val argExpression = arg.getArgumentExpression()
            if (argExpression?.isSimplifiableTo(oldReceiver) == true)
                appendFixedText(parameterName)
            else
                appendExpression(argExpression)
        }
        appendFixedText(") }")
    }
}

internal fun IfThenTransformationData.conditionHasIncompatibleTypes(context: BindingContext): Boolean {
    val isExpression = condition as? KtIsExpression ?: return false

    val targetType = context[BindingContext.TYPE, isExpression.typeReference] ?: return true
    if (TypeUtils.isNullableType(targetType)) return true
    // TODO: the following check can be removed after fix of KT-14576
    val originalType = checkedExpression.getType(context) ?: return true

    return !targetType.isSubtypeOf(originalType)
}

internal fun KtExpression?.isClauseTransformableToLetOnly(receiver: KtExpression?) =
    this is KtCallExpression && (resolveToCall()?.getImplicitReceiverValue() == null || receiver !is KtThisExpression)

fun KtIfExpression.shouldBeTransformed(): Boolean = when (val condition = condition) {
    is KtBinaryExpression -> {
        val baseClause = (if (condition.operationToken == KtTokens.EQEQ) `else` else then)?.getSingleUnwrappedStatementOrThis()
        !baseClause.isClauseTransformableToLetOnly(condition.checkedExpression())
    }
    else -> false
}

fun KtIfExpression.fromIfKeywordToRightParenthesisTextRangeInThis(): TextRange {
    val rightOffset = rightParenthesis?.endOffset ?: return ifKeyword.textRangeIn(this)
    return TextRange(ifKeyword.startOffset, rightOffset).shiftLeft(startOffset)
}

internal fun KtExpression.hasNullableType(context: BindingContext): Boolean {
    val type = getType(context) ?: return true
    return TypeUtils.isNullableType(type)
}

internal fun KtExpression.hasFirstReceiverOf(receiver: KtExpression): Boolean {
    val actualReceiver = (this as? KtDotQualifiedExpression)?.getLeftMostReceiverExpression() ?: return false
    return actualReceiver.isSimplifiableTo(receiver)
}

private fun KtExpression.insertSafeCalls(factory: KtPsiFactory): KtExpression {
    if (this !is KtQualifiedExpression) return this
    val replaced = (if (this is KtDotQualifiedExpression) {
        this.replaced(factory.createExpressionByPattern("$0?.$1", receiverExpression, selectorExpression!!))
    } else this) as KtQualifiedExpression
    replaced.receiverExpression.let { it.replace(it.insertSafeCalls(factory)) }
    return replaced
}

internal fun KtExpression.isElseIf() = parent.node.elementType == KtNodeTypes.ELSE
