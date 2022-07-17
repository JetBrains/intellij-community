// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.references

import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.references.ReferenceAccess

//TODO Make valid FIR implementation
class ReadWriteAccessCheckerDescriptorsFirImpl : ReadWriteAccessChecker {
    override fun readWriteAccessWithFullExpressionByResolve(assignment: KtBinaryExpression): Pair<ReferenceAccess, KtExpression>? {
        // TODO
        return null
    }
}