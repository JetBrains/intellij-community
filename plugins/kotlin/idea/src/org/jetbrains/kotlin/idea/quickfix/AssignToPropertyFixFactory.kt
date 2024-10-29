// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.scopes.utils.getImplicitReceiversHierarchy
import org.jetbrains.kotlin.types.KotlinType

internal object AssignToPropertyFixFactory : KotlinSingleIntentionActionFactory() {
    private fun KtCallableDeclaration.hasNameAndTypeOf(name: Name, type: KotlinType) =
        nameAsName == name && (resolveToDescriptorIfAny() as? CallableDescriptor)?.returnType == type

    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val expression = diagnostic.psiElement as? KtNameReferenceExpression ?: return null

        val containingClass = expression.containingClass() ?: return null
        val right = (expression.parent as? KtBinaryExpression)?.right ?: return null
        val type = expression.analyze().getType(right) ?: return null
        val name = expression.getReferencedNameAsName()

        val inSecondaryConstructor = expression.getStrictParentOfType<KtSecondaryConstructor>() != null
        val hasAssignableProperty = containingClass.getProperties().any {
            (inSecondaryConstructor || it.isVar) && it.hasNameAndTypeOf(name, type)
        }
        val hasAssignablePropertyInPrimaryConstructor = containingClass.primaryConstructor?.valueParameters?.any {
            it.valOrVarKeyword?.node?.elementType == KtTokens.VAR_KEYWORD &&
                    it.hasNameAndTypeOf(name, type)
        } ?: false

        if (!hasAssignableProperty && !hasAssignablePropertyInPrimaryConstructor) return null

        val hasSingleImplicitReceiver = expression.getResolutionScope().getImplicitReceiversHierarchy().size == 1
        return AssignToPropertyFix(expression, hasSingleImplicitReceiver).asIntention()
    }
}
