// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyze
import org.jetbrains.kotlin.idea.codeinsight.utils.RangeKtExpressionType
import org.jetbrains.kotlin.idea.codeinsight.utils.getRangeBinaryExpressionType
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.builtIns

internal fun KtExpression.isRangeExpression(context: Lazy<BindingContext>? = null): Boolean = getRangeBinaryExpressionType(context) != null

internal fun KtExpression.isComparable(context: BindingContext): Boolean {
    val valType = getType(context) ?: return false
    return DescriptorUtils.isSubtypeOfClass(valType, valType.builtIns.comparable)
}

internal fun KtExpression.getRangeBinaryExpressionType(context: Lazy<BindingContext>? = null): RangeKtExpressionType? {
    return getRangeBinaryExpressionType(this).takeIf {
        val notNullContext = context?.value ?: safeAnalyze(BodyResolveMode.PARTIAL)
        getResolvedCall(notNullContext)?.resultingDescriptor?.fqNameOrNull()?.asString()?.startsWith("kotlin.") == true
    }
}
