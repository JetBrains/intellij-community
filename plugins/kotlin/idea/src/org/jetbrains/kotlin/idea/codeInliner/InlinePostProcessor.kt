// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInliner

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.core.OptionalParametersHelper
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.canMoveLambdaOutsideParentheses
import org.jetbrains.kotlin.idea.inspections.RedundantUnitExpressionInspection
import org.jetbrains.kotlin.idea.intentions.RemoveExplicitTypeArgumentsIntention
import org.jetbrains.kotlin.idea.refactoring.inline.KotlinInlineAnonymousFunctionProcessor
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.AbstractInlinePostProcessor
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.DEFAULT_PARAMETER_VALUE_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.MAKE_ARGUMENT_NAMED_KEY
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.InlineDataKeys.USER_CODE_KEY
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.resolve.CompileTimeConstantUtils
import org.jetbrains.kotlin.resolve.calls.model.ArgumentMatch
import org.jetbrains.kotlin.resolve.calls.model.isReallySuccess
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.ArrayList
import java.util.LinkedHashSet
import kotlin.collections.asReversed
import kotlin.collections.dropWhile
import kotlin.collections.isNotEmpty
import kotlin.collections.mapNotNull
import kotlin.collections.orEmpty
import kotlin.let
import kotlin.to

object InlinePostProcessor: AbstractInlinePostProcessor() {
    override fun shortenReferences(pointers: List<SmartPsiElementPointer<KtElement>>): List<KtElement> {
        val shortenFilter = { element: PsiElement ->
            if (element.getCopyableUserData(USER_CODE_KEY) != null) {
                ShortenReferences.FilterResult.SKIP
            } else {
                val thisReceiver = (element as? KtQualifiedExpression)?.receiverExpression as? KtThisExpression
                if (thisReceiver != null && thisReceiver.getCopyableUserData(USER_CODE_KEY) != null) // don't remove explicit 'this' coming from user's code
                    ShortenReferences.FilterResult.GO_INSIDE
                else
                    ShortenReferences.FilterResult.PROCESS
            }
        }

        // can simplify to single call after KTIJ-646
        val newElements = pointers.mapNotNull {
            it.element?.let { element ->
                ShortenReferences { ShortenReferences.Options(removeThis = true) }.process(element, elementFilter = shortenFilter)
            }
        }
        return newElements
    }

    override fun removeRedundantLambdasAndAnonymousFunctions(pointer: SmartPsiElementPointer<KtElement>) {
        val element = pointer.element ?: return
        for (function in element.collectDescendantsOfType<KtFunction>().asReversed()) {
            if (function.hasBody()) {
                val call = KotlinInlineAnonymousFunctionProcessor.findCallExpression(function)
                if (call != null) {
                    KotlinInlineAnonymousFunctionProcessor.performRefactoring(call, editor = null)
                }
            }
        }
    }

    override fun removeRedundantUnitExpressions(pointer: SmartPsiElementPointer<KtElement>) {
        pointer.element?.forEachDescendantOfType<KtReferenceExpression> {
            if (RedundantUnitExpressionInspection.Util.isRedundantUnit(it)) {
                it.delete()
            }
        }
    }

    override fun introduceNamedArguments(pointer: SmartPsiElementPointer<KtElement>) {
        val element = pointer.element ?: return
        val psiFactory = KtPsiFactory.contextual(element)
        val callsToProcess = LinkedHashSet<KtCallExpression>()
        element.forEachDescendantOfType<KtValueArgument> {
            if (it.getCopyableUserData(MAKE_ARGUMENT_NAMED_KEY) != null && !it.isNamed()) {
                val callExpression = (it.parent as? KtValueArgumentList)?.parent as? KtCallExpression
                callsToProcess.addIfNotNull(callExpression)
            }
        }

        for (callExpression in callsToProcess) {
            val resolvedCall = callExpression.resolveToCall() ?: return
            if (!resolvedCall.isReallySuccess()) return

            val argumentsToMakeNamed = callExpression.valueArguments.dropWhile { it.getCopyableUserData(MAKE_ARGUMENT_NAMED_KEY) == null }
            for (argument in argumentsToMakeNamed) {
                if (argument.isNamed()) continue
                if (argument is KtLambdaArgument) continue
                val argumentMatch = resolvedCall.getArgumentMapping(argument) as ArgumentMatch
                val name = argumentMatch.valueParameter.name
                //TODO: not always correct for vararg's
                val newArgument = psiFactory.createArgument(argument.getArgumentExpression()!!, name, argument.getSpreadElement() != null)

                if (argument.getCopyableUserData(DEFAULT_PARAMETER_VALUE_KEY) != null) {
                    newArgument.putCopyableUserData(DEFAULT_PARAMETER_VALUE_KEY, Unit)
                }

                argument.replace(newArgument)
            }
        }
    }

    override fun dropArgumentsForDefaultValues(pointer: SmartPsiElementPointer<KtElement>) {
        val result = pointer.element ?: return
        val project = result.project
        val newBindingContext = result.analyze()
        val argumentsToDrop = ArrayList<ValueArgument>()

        // we drop only those arguments that added to the code from some parameter's default
        fun canDropArgument(argument: ValueArgument) = (argument as KtValueArgument).getCopyableUserData(DEFAULT_PARAMETER_VALUE_KEY) != null

        result.forEachDescendantOfType<KtCallElement> { callExpression ->
            val resolvedCall = callExpression.getResolvedCall(newBindingContext) ?: return@forEachDescendantOfType

            argumentsToDrop.addAll(OptionalParametersHelper.detectArgumentsToDropForDefaults(resolvedCall, project, ::canDropArgument))
        }

        for (argument in argumentsToDrop) {
            argument as KtValueArgument
            val argumentList = argument.parent as KtValueArgumentList
            argumentList.removeArgument(argument)
            if (argumentList.arguments.isEmpty()) {
                val callExpression = argumentList.parent as KtCallElement
                if (callExpression.lambdaArguments.isNotEmpty()) {
                    argumentList.delete()
                }
            }
        }
    }

    override fun removeExplicitTypeArguments(pointer: SmartPsiElementPointer<KtElement>) {
        val result = pointer.element ?: return
        for (typeArgumentList in result.collectDescendantsOfType<KtTypeArgumentList>(canGoInside = { it.getCopyableUserData(USER_CODE_KEY) == null }).asReversed()) {
            if (RemoveExplicitTypeArgumentsIntention.isApplicableTo(typeArgumentList, approximateFlexible = true)) {
                typeArgumentList.delete()
            }
        }
    }

    override fun convertFunctionToLambdaAndMoveOutsideParentheses(function: KtNamedFunction) {}

    override fun simplifySpreadArrayOfArguments(pointer: SmartPsiElementPointer<KtElement>) {
        val result = pointer.element ?: return
        //TODO: test for nested

        val argumentsToExpand = ArrayList<Pair<KtValueArgument, Collection<KtValueArgument>>>()

        result.forEachDescendantOfType<KtValueArgument>(canGoInside = { it.getCopyableUserData(USER_CODE_KEY) == null }) { argument ->
            if (argument.getSpreadElement() != null && !argument.isNamed()) {
                val argumentExpression = argument.getArgumentExpression() ?: return@forEachDescendantOfType
                val resolvedCall = argumentExpression.resolveToCall() ?: return@forEachDescendantOfType
                val callExpression = resolvedCall.call.callElement as? KtCallElement ?: return@forEachDescendantOfType
                if (CompileTimeConstantUtils.isArrayFunctionCall(resolvedCall)) {
                    argumentsToExpand.add(argument to callExpression.valueArgumentList?.arguments.orEmpty())
                }
            }
        }

        for ((argument, replacements) in argumentsToExpand) {
            argument.replaceByMultiple(replacements)
        }
    }

    private fun KtValueArgument.replaceByMultiple(arguments: Collection<KtValueArgument>) {
        val list = parent as KtValueArgumentList
        if (arguments.isEmpty()) {
            list.removeArgument(this)
        } else {
            var anchor = this
            for (argument in arguments) {
                anchor = list.addArgumentAfter(argument, anchor)
            }
            list.removeArgument(this)
        }
    }

    override fun canMoveLambdaOutsideParentheses(expr: KtCallExpression): Boolean {
        return expr.canMoveLambdaOutsideParentheses(skipComplexCalls = false)
    }
}