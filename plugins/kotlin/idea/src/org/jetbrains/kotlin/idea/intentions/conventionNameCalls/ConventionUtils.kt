// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.conventionNameCalls

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.findOriginalTopMostOverriddenDescriptors

fun KtExpression.isAnyEquals(): Boolean {
    val resolvedCall = resolveToCall() ?: return false
    return (resolvedCall.resultingDescriptor as? FunctionDescriptor)?.isAnyEquals() == true
}

fun FunctionDescriptor.isAnyEquals(): Boolean {
    val overriddenDescriptors = findOriginalTopMostOverriddenDescriptors()
    return overriddenDescriptors.any { it.fqNameUnsafe.asString() == "kotlin.Any.equals" }
}

fun FunctionDescriptor.isAnyHashCode(): Boolean {
    val overriddenDescriptors = findOriginalTopMostOverriddenDescriptors()
    return overriddenDescriptors.any { it.fqNameUnsafe.asString() == "kotlin.Any.hashCode" }
}

fun FunctionDescriptor.isAnyToString(): Boolean {
    val overriddenDescriptors = findOriginalTopMostOverriddenDescriptors()
    return overriddenDescriptors.any { it.fqNameUnsafe.asString() == "kotlin.Any.toString" }
}
