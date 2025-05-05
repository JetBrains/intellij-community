// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.descriptors.ValueDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

internal class AddSuspendModifierFix(
    element: KtModifierListOwner,
    private val declarationName: String?
) : AddModifierFix(element, KtTokens.SUSPEND_KEYWORD) {

    override fun getPresentation(context: ActionContext, element: KtModifierListOwner): Presentation {
        val actionName = when (element) {
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

            else -> familyName
        }
        return Presentation.of(actionName)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val element = diagnostic.psiElement as? KtElement ?: return null
            val function = element.containingFunction() ?: return null
            val functionName = function.name ?: return null

            return AddSuspendModifierFix(function, functionName).asIntention()
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

            return AddSuspendModifierFix(variableTypeReference, declaration.name).asIntention()
        }
    }
}
