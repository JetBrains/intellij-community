// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.compilerPlugin.assignment.k1

import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.quickfix.AbstractImportFix
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.types.expressions.OperatorConventions

/**
 * The idea behind this plugin specific import fix is to convey to the outer logic that import suggestions should be made based on an
 * assumption that the call type is actually `CallTypeAndReceiver.DOT` (as it is under the cover) but not `CallTypeAndReceiver.OPERATOR`
 * (as it is in the source code).
 */
class AssignmentPluginImportFix(expression: KtOperationReferenceExpression): AbstractImportFix(expression, MyFactory) {

    companion object MyFactory : FactoryWithUnresolvedReferenceQuickFix() {
        override fun areActionsAvailable(diagnostic: Diagnostic): Boolean {
            val expression = extractExpression(diagnostic)
            return expression != null && expression.references.isNotEmpty()
        }

        override fun createImportAction(diagnostic: Diagnostic): AssignmentPluginImportFix? =
            extractExpression(diagnostic)?.let(::AssignmentPluginImportFix)

        private fun extractExpression(diagnostic: Diagnostic): KtOperationReferenceExpression? =
            diagnostic.psiElement as? KtOperationReferenceExpression
    }

    override fun getCallTypeAndReceiver(): CallTypeAndReceiver<*, *>? {
        return element?.let { CallTypeAndReceiver.DOT(it.getReceiverExpression()!!) }
    }

    override val importNames: Collection<Name>
        get() = listOf(OperatorConventions.ASSIGN_METHOD)
}