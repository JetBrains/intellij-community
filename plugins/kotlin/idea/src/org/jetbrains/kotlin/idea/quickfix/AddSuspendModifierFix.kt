// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.descriptors.ValueDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

class AddSuspendModifierFix(
    element: KtModifierListOwner,
    private val declarationName: String?
) : AddModifierFixFE10(element, KtTokens.SUSPEND_KEYWORD) {

    override fun getText() = when (element) {
        is KtNamedFunction -> {
            if (declarationName != null) {
                KotlinBundle.message("fix.add.suspend.modifier.function", declarationName)
            } else {
                KotlinBundle.message("fix.add.suspend.modifier.function.generic")
            }
        }
        is KtTypeReference -> {
            if (declarationName != null) {
                KotlinBundle.message("fix.add.suspend.modifier.receiver", declarationName)
            } else {
                KotlinBundle.message("fix.add.suspend.modifier.receiver.generic")
            }
        }
        else -> super.getText()
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val element = diagnostic.psiElement as? KtElement ?: return null
            val function = element.containingFunction() ?: return null
            val functionName = function.name ?: return null

            return AddSuspendModifierFix(function, functionName)
        }
    }

    object UnresolvedReferenceFactory : KotlinSingleIntentionActionFactory() {

        private val suspendExtensionNames = setOf("startCoroutine", "createCoroutine")

        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val refExpr = diagnostic.psiElement as? KtNameReferenceExpression ?: return null
            if (refExpr.getReferencedName() !in suspendExtensionNames) return null

            val callParent = refExpr.parent as? KtCallExpression ?: return null
            val qualifiedGrandParent = callParent.parent as? KtQualifiedExpression ?: return null
            if (callParent !== qualifiedGrandParent.selectorExpression || refExpr !== callParent.calleeExpression) return null
            val receiver = qualifiedGrandParent.receiverExpression as? KtNameReferenceExpression ?: return null

            val receiverDescriptor = receiver.resolveToCall()?.resultingDescriptor as? ValueDescriptor ?: return null
            if (!receiverDescriptor.type.isFunctionType) return null
            val declaration = DescriptorToSourceUtils.descriptorToDeclaration(receiverDescriptor) as? KtCallableDeclaration
                ?: return null
            if (declaration is KtFunction) return null
            val variableTypeReference = declaration.typeReference ?: return null

            return AddSuspendModifierFix(variableTypeReference, declaration.name)
        }
    }
}
