// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.references

import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.model.isReallySuccess
import org.jetbrains.kotlin.resolve.references.ReferenceAccess
import org.jetbrains.kotlin.types.expressions.OperatorConventions

class ReadWriteAccessCheckerDescriptorsImpl : ReadWriteAccessChecker {
    override fun readWriteAccessWithFullExpressionByResolve(assignment: KtBinaryExpression): Pair<ReferenceAccess, KtExpression>? {
        val resolvedCall = assignment.resolveToCall() ?: return null
        if (!resolvedCall.isReallySuccess()) return null
        return if (resolvedCall.resultingDescriptor.name in OperatorConventions.ASSIGNMENT_OPERATIONS.values)
            ReferenceAccess.READ to assignment
        else
            null
    }
}
