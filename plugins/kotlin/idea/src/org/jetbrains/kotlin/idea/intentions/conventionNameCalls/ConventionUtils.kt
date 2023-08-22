// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.conventionNameCalls

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.findOriginalTopMostOverriddenDescriptors

const val HASH_CODE = "hashCode"
const val EQUALS = "equals"
const val TO_STRING = "toString"

const val KOTLIN_ANY_HASH_CODE_FQN = "kotlin.Any.$HASH_CODE"
const val KOTLIN_ANY_EQUALS_FQN = "kotlin.Any.equals"
const val KOTLIN_TO_STRING_FQN = "kotlin.Any.$TO_STRING"

fun KtExpression.isAnyEquals(): Boolean {
    val resolvedCall = resolveToCall() ?: return false
    return (resolvedCall.resultingDescriptor as? FunctionDescriptor)?.isAnyEquals() == true
}

fun FunctionDescriptor.isAnyEquals(): Boolean {
    val overriddenDescriptors = findOriginalTopMostOverriddenDescriptors()
    return overriddenDescriptors.any { it.fqNameUnsafe.asString() == KOTLIN_ANY_EQUALS_FQN }
}

fun FunctionDescriptor.isAnyHashCode(): Boolean {
    val overriddenDescriptors = findOriginalTopMostOverriddenDescriptors()
    return overriddenDescriptors.any { it.fqNameUnsafe.asString() == KOTLIN_ANY_HASH_CODE_FQN }
}

fun FunctionDescriptor.isAnyToString(): Boolean {
    val overriddenDescriptors = findOriginalTopMostOverriddenDescriptors()
    return overriddenDescriptors.any { it.fqNameUnsafe.asString() == KOTLIN_TO_STRING_FQN }
}
