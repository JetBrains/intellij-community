// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.removeUnnecessaryParentheses
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPsiUtil

@ApiStatus.Internal
@IntellijInternalApi
class RemoveUnnecessaryParenthesesIntention : SelfTargetingRangeIntention<KtParenthesizedExpression>(
    KtParenthesizedExpression::class.java, KotlinBundle.messagePointer("remove.unnecessary.parentheses")
) {
    override fun applicabilityRange(element: KtParenthesizedExpression): TextRange? {
        element.expression ?: return null
        if (!KtPsiUtil.areParenthesesUseless(element)) return null
        return element.textRange
    }

    override fun applyTo(element: KtParenthesizedExpression, editor: Editor?) {
        element.removeUnnecessaryParentheses()
    }
}
