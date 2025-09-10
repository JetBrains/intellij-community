// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.completion.canBeUsedWithoutNameInCall
import org.jetbrains.kotlin.idea.completion.collectAllArgumentsThatCanBeUsedWithoutName
import org.jetbrains.kotlin.idea.intentions.RemoveAllArgumentNamesIntention.Holder.removeArgumentNames
import org.jetbrains.kotlin.psi.KtCallElement
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class RemoveSingleArgumentNameIntention : SelfTargetingRangeIntention<KtValueArgument>(
    KtValueArgument::class.java,
    KotlinBundle.messagePointer("remove.argument.name")
) {
    override fun applicabilityRange(element: KtValueArgument): TextRange? {
        if (!element.isNamed()) return null
        val argumentExpression = element.getArgumentExpression() ?: return null
        val resolvedCall = element.getStrictParentOfType<KtCallElement>()?.resolveToCall() ?: return null
        if (!element.canBeUsedWithoutNameInCall(resolvedCall)) return null
        return TextRange(element.startOffset, argumentExpression.startOffset)
    }

    override fun applyTo(element: KtValueArgument, editor: Editor?) {
        val argumentList = element.parent as? KtValueArgumentList ?: return
        val resolvedCall = (argumentList.parent as? KtCallElement)?.resolveToCall() ?: return
        val argumentsThatCanBeUsedWithoutName = collectAllArgumentsThatCanBeUsedWithoutName(resolvedCall).dropLastWhile {
            it.argument != element
        }
        argumentList.removeArgumentNames(argumentsThatCanBeUsedWithoutName, removeOnlyLastArgumentName = true)
    }
}
