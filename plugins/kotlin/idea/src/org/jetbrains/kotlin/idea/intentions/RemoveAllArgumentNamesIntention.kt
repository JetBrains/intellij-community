// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.createArgumentWithoutName
import org.jetbrains.kotlin.idea.completion.ArgumentThatCanBeUsedWithoutName
import org.jetbrains.kotlin.idea.completion.collectAllArgumentsThatCanBeUsedWithoutName
import org.jetbrains.kotlin.idea.intentions.RemoveAllArgumentNamesIntention.Holder.removeArgumentNames
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.resolve.calls.components.isVararg

class RemoveAllArgumentNamesIntention : SelfTargetingIntention<KtCallElement>(
    KtCallElement::class.java,
    KotlinBundle.messagePointer("remove.all.argument.names")
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

    object Holder {
        fun KtValueArgumentList.removeArgumentNames(
            argumentsThatCanBeUsedWithoutName: List<ArgumentThatCanBeUsedWithoutName>,
            removeOnlyLastArgumentName: Boolean = false
        ) {
            val lastArgument = argumentsThatCanBeUsedWithoutName.lastOrNull()?.argument
            argumentsThatCanBeUsedWithoutName.reversed().forEach { (argument, parameter) ->
                if (argument is KtLambdaArgument) return@forEach
                val newArguments = if (!argument.isNamed() || (removeOnlyLastArgumentName && argument != lastArgument)) {
                    listOf(argument.copied())
                } else {
                    createArgumentWithoutName(
                        argument,
                        isVararg = parameter?.isVararg == true,
                        isArrayOf = (argument.getArgumentExpression() as? KtCallExpression)?.isArrayOfFunction() == true
                    )
                }
                removeArgument(argument)
                newArguments.reversed().forEach { addArgumentBefore(it, arguments.firstOrNull()) }
            }
        }
    }
}
