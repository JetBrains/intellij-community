// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.references

import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.references.ReferenceAccess
import org.jetbrains.kotlin.types.expressions.OperatorConventions

class ReadWriteAccessCheckerDescriptorsFirImpl : ReadWriteAccessChecker {
    override fun readWriteAccessWithFullExpressionByResolve(assignment: KtBinaryExpression): Pair<ReferenceAccess, KtExpression>? {
        val function = assignment.operationReference.mainReference.resolve() as? KtNamedFunction ?: return null
        val name = function.name ?: return null
        return if (Name.identifier(name) in OperatorConventions.ASSIGNMENT_OPERATIONS.values)
            ReferenceAccess.READ to assignment
        else
            null
    }
}