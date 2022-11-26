// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.util.getRangeBinaryExpressionType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe

internal fun KtExpression.getArguments() = when (this) {
    is KtBinaryExpression -> this.left to this.right
    is KtDotQualifiedExpression -> this.receiverExpression to this.callExpression?.valueArguments?.singleOrNull()?.getArgumentExpression()
    else -> null
}

class ReplaceUntilWithRangeToIntention : SelfTargetingIntention<KtExpression>(
    KtExpression::class.java,
    KotlinBundle.lazyMessage("replace.with.0.operator", "..")
) {
    override fun isApplicableTo(element: KtExpression, caretOffset: Int): Boolean {
        if (element !is KtBinaryExpression && element !is KtDotQualifiedExpression) return false
        val fqName = element.getCallableDescriptor()?.fqNameUnsafe?.asString() ?: return false
        return fqName == "kotlin.ranges.until"
    }

    override fun applyTo(element: KtExpression, editor: Editor?) {
        val args = element.getArguments() ?: return
        val psiFactory = KtPsiFactory(element.project)
        element.replace(psiFactory.createExpressionByPattern("$0..$1 - 1", args.first ?: return, args.second ?: return))
    }
}