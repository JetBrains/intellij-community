// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import com.intellij.util.asSafely
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyze
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.builtIns

internal fun KtExpression.isRangeExpression(context: Lazy<BindingContext>? = null): Boolean = getRangeBinaryExpressionType(context) != null

internal fun KtExpression.isComparable(): Boolean {
    val context = safeAnalyze(BodyResolveMode.PARTIAL)
    val valType = getType(context) ?: return false
    return DescriptorUtils.isSubtypeOfClass(valType, valType.builtIns.comparable)
}

internal fun KtExpression.getRangeBinaryExpressionType(context: Lazy<BindingContext>? = null): RangeKtExpressionType? {
    val binaryExprName = asSafely<KtBinaryExpression>()?.operationReference?.getReferencedNameAsName()?.asString()
    val dotQualifiedName = asSafely<KtDotQualifiedExpression>()?.callExpression?.calleeExpression?.text
    val name = binaryExprName ?: dotQualifiedName
    return when {
        binaryExprName == ".." || dotQualifiedName == "rangeTo" -> RangeKtExpressionType.RANGE_TO
        binaryExprName == "..<" || dotQualifiedName == "rangeUntil" -> RangeKtExpressionType.RANGE_UNTIL
        name == "downTo" -> RangeKtExpressionType.DOWN_TO
        name == "until" -> RangeKtExpressionType.UNTIL
        else -> null
    }?.takeIf {
        val notNullContext = context?.value ?: safeAnalyze(BodyResolveMode.PARTIAL)
        getResolvedCall(notNullContext)?.resultingDescriptor?.fqNameOrNull()?.asString()?.startsWith("kotlin.") == true
    }
}

enum class RangeKtExpressionType {
    RANGE_TO, RANGE_UNTIL, DOWN_TO, UNTIL
}
