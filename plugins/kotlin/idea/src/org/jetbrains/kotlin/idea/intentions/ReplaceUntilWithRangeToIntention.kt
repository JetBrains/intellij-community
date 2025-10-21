// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isUnsignedNumberType

internal fun KtExpression.getArguments() = when (this) {
    is KtBinaryExpression -> this.left to this.right
    is KtDotQualifiedExpression -> this.receiverExpression to this.callExpression?.valueArguments?.singleOrNull()?.getArgumentExpression()
    else -> null
}

class ReplaceUntilWithRangeToIntention : SelfTargetingIntention<KtExpression>(
    KtExpression::class.java,
    KotlinBundle.messagePointer("replace.with.0.operator", "..")
) {
    override fun isApplicableTo(element: KtExpression, caretOffset: Int): Boolean {
        if (element !is KtBinaryExpression && element !is KtDotQualifiedExpression) return false
        val fqName = element.getCallableDescriptor()?.fqNameOrNull() ?: return false
        return fqName in untilFqNames
    }

    override fun applyTo(element: KtExpression, editor: Editor?) {
        val args = element.getArguments() ?: return
        val left = args.first ?: return
        val right = args.second ?: return

        val context = element.analyze(BodyResolveMode.PARTIAL)
        val isUnsignedNumber = args.second?.getType(context)?.isUnsignedNumberType() == true
        val pattern = "$0..$1 - 1${if (isUnsignedNumber) "u" else ""}"

        val psiFactory = KtPsiFactory(element.project)
        element.replace(psiFactory.createExpressionByPattern(pattern, left, right))
    }
}

private val untilFqNames: Set<FqName> = listOf(
    "kotlin.ranges.until",
    "kotlin.Byte.rangeUntil",
    "kotlin.Short.rangeUntil",
    "kotlin.Int.rangeUntil",
    "kotlin.Long.rangeUntil",
    "kotlin.Char.rangeUntil",
    "kotlin.UInt.rangeUntil",
    "kotlin.ULong.rangeUntil",
).map(::FqName).toSet()