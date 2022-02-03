// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.completion.ArgumentThatCanBeUsedWithoutName
import org.jetbrains.kotlin.idea.completion.collectAllArgumentsThatCanBeUsedWithoutName
import org.jetbrains.kotlin.idea.core.copied
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.components.isVararg

class RemoveAllArgumentNamesIntention : SelfTargetingIntention<KtCallElement>(
    KtCallElement::class.java,
    KotlinBundle.lazyMessage("remove.all.argument.names")
) {
    override fun isApplicableTo(element: KtCallElement, caretOffset: Int): Boolean {
        val arguments = element.valueArgumentList?.arguments ?: return false
        if (arguments.count { it.isNamed() } < 2) return false
        val resolvedCall = element.resolveToCall() ?: return false
        return collectAllArgumentsThatCanBeUsedWithoutName(resolvedCall).any()
    }

    override fun applyTo(element: KtCallElement, editor: Editor?) {
        val argumentList = element.valueArgumentList ?: return
        val resolvedCall = element.resolveToCall() ?: return
        argumentList.removeArgumentNames(collectAllArgumentsThatCanBeUsedWithoutName(resolvedCall))
    }

    companion object {
        fun KtValueArgumentList.removeArgumentNames(
            argumentsThatCanBeUsedWithoutName: List<ArgumentThatCanBeUsedWithoutName>,
            removeOnlyLastArgumentName: Boolean = false
        ) {
            val lastArgument = argumentsThatCanBeUsedWithoutName.lastOrNull()?.argument
            argumentsThatCanBeUsedWithoutName.reversed().forEach { (argument, parameter) ->
                val newArguments = if (!argument.isNamed() || (removeOnlyLastArgumentName && argument != lastArgument)) {
                    listOf(argument.copied())
                } else {
                    createArgumentWithoutName(argument, parameter)
                }
                removeArgument(argument)
                newArguments.reversed().forEach { addArgumentBefore(it, arguments.firstOrNull()) }
            }
        }

        private fun createArgumentWithoutName(argument: KtValueArgument, parameter: ValueParameterDescriptor?): List<KtValueArgument> {
            if (!argument.isNamed()) return listOf(argument.copied())
            val argumentExpr = argument.getArgumentExpression() ?: return emptyList()
            val psiFactory = KtPsiFactory(argument)
            val isVararg = parameter?.isVararg ?: false
            return when {
                isVararg && argumentExpr is KtCollectionLiteralExpression ->
                    argumentExpr.getInnerExpressions().map { psiFactory.createArgument(it) }
                isVararg && argumentExpr is KtCallExpression && argumentExpr.isArrayOfMethod() ->
                    argumentExpr.valueArguments.map { psiFactory.createArgument(it.getArgumentExpression()) }
                else ->
                    listOf(psiFactory.createArgument(argumentExpr, null, isVararg))
            }
        }
    }
}
