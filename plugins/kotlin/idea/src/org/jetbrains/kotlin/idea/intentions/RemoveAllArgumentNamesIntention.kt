// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.completion.placedOnItsOwnPositionInCall
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.resolve.calls.callUtil.getParameterForArgument
import org.jetbrains.kotlin.resolve.calls.components.isVararg

class RemoveAllArgumentNamesIntention: SelfTargetingIntention<KtCallElement>(
    KtCallElement::class.java,
    KotlinBundle.lazyMessage("remove.all.argument.names")
) {
    override fun isApplicableTo(element: KtCallElement, caretOffset: Int): Boolean {
        val arguments = element.valueArguments
        if (arguments.count { it.isNamed() } < 2) return false
        val collectionLiteral = arguments.firstOrNull { it.getArgumentExpression() is KtCollectionLiteralExpression }
        if (collectionLiteral != null && collectionLiteral != arguments.last()) return false

        val resolvedCall = element.resolveToCall() ?: return false
        val parameters = resolvedCall.resultingDescriptor.valueParameters
        val varargParameter = parameters.firstOrNull { it.isVararg }
        if (varargParameter != null && varargParameter != parameters.lastOrNull()) return false
        return arguments.all { it.placedOnItsOwnPositionInCall(resolvedCall) || resolvedCall.getParameterForArgument(it)?.isVararg == true }
    }

    override fun applyTo(element: KtCallElement, editor: Editor?) {
        element.valueArguments.filterIsInstance<KtValueArgument>().forEach { RemoveArgumentNameIntention.removeName(it) }
    }
}
