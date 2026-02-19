// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.checkers.ConstModifierChecker
import org.jetbrains.kotlin.resolve.source.PsiSourceElement

internal object ConstFixFactory : KotlinSingleIntentionActionFactory() {
    override fun createAction(diagnostic: Diagnostic): IntentionAction? {
        val expr = when (val psi = diagnostic.psiElement) {
            is KtReferenceExpression -> psi
            is KtQualifiedExpression -> psi.selectorExpression as? KtReferenceExpression
            else -> null
        } ?: return null

        val targetDescriptor = expr.resolveToCall()?.resultingDescriptor as? VariableDescriptor ?: return null
        val declaration = (targetDescriptor.source as? PsiSourceElement)?.psi as? KtProperty ?: return null
        if (ConstModifierChecker.canBeConst(declaration, declaration, targetDescriptor)) {
            return AddConstModifierFix(declaration).asIntention()
        }
        return null
    }
}