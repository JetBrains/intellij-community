// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.completion.canBeUsedWithoutNameInCall
import org.jetbrains.kotlin.idea.completion.placedOnItsOwnPositionInCall
import org.jetbrains.kotlin.idea.core.copied
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.calls.callUtil.getParameterForArgument
import org.jetbrains.kotlin.resolve.calls.components.hasDefaultValue
import org.jetbrains.kotlin.resolve.calls.components.isVararg
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

class RemoveAllArgumentNamesIntention: SelfTargetingIntention<KtCallElement>(
    KtCallElement::class.java,
    KotlinBundle.lazyMessage("remove.all.argument.names")
) {
    override fun isApplicableTo(element: KtCallElement, caretOffset: Int): Boolean {
        val arguments = element.valueArgumentList?.arguments ?: return false
        if (arguments.count { it.isNamed() } < 2) return false
        val resolvedCall = element.resolveToCall() ?: return false
        return arguments.all { it.canBeUsedWithoutNameInCall(resolvedCall, noOtherNamedArguments = true) }
    }

    override fun applyTo(element: KtCallElement, editor: Editor?) {
        val valueArgumentList = element.valueArgumentList ?: return
        val arguments = valueArgumentList.arguments
        val resolvedCall = element.resolveToCall() ?: return
        if (arguments.all { it.placedOnItsOwnPositionInCall(resolvedCall) || it.isVararg(resolvedCall) }) {
            arguments.forEach { RemoveArgumentNameIntention.removeName(it) }
            return
        }

        val parameters = resolvedCall.resultingDescriptor.valueParameters
        val argumentSize = arguments.size
        val parameterSize = parameters.size
        val defaultArgumentIndex = parameters.indexOfFirst { it.hasDefaultValue() }
        val expectedArgumentIndexes = arguments.mapIndexedNotNull { index, argument ->
            if (!argument.isNamed()) return@mapIndexedNotNull null
            val parameter = resolvedCall.getParameterForArgument(argument) ?: return@mapIndexedNotNull null
            if (argument.placedOnItsOwnPositionInCall(resolvedCall) ||
                argumentSize >= parameterSize ||
                defaultArgumentIndex == -1 ||
                defaultArgumentIndex >= parameter.index
            ) RemoveArgumentNameIntention.removeName(argument)
            index to parameter.index
        }.sortedBy { it.second }.map { it.first }

        if (argumentSize != expectedArgumentIndexes.size) return
        val argumentIndexes = (0 until argumentSize).zip(expectedArgumentIndexes).toMap()
        if (argumentIndexes.all { it.key == it.value }) return
        val newArguments = valueArgumentList.arguments
        val copiedArguments = newArguments.map { it.copied() }
        newArguments.forEachIndexed { index, argument ->
            val expectedIndex = argumentIndexes[index]
            if (expectedIndex != null && expectedIndex != index) {
                argument.replace(copiedArguments[expectedIndex])
            }
        }
    }

    private fun KtValueArgument.isVararg(resolvedCall: ResolvedCall<out CallableDescriptor>) =
        resolvedCall.getParameterForArgument(this)?.isVararg == true
}
