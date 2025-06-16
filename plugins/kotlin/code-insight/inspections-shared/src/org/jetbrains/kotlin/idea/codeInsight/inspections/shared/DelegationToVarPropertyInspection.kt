// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.asQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ChangeVariableMutabilityFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class DelegationToVarPropertyInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        delegatedSuperTypeEntry(fun(delegatedSuperTypeEntry) {
            val delegateExpression = delegatedSuperTypeEntry.delegateExpression ?: return
            val parameter = delegateExpression.mainReference?.resolve() as? KtParameter ?: return
            if (parameter.valOrVarKeyword?.node?.elementType != KtTokens.VAR_KEYWORD) return

            val containingClass = parameter.containingClassOrObject as? KtClass ?: return
            val isUsedForOtherClass = containingClass != delegatedSuperTypeEntry.getStrictParentOfType<KtClass>()

            val canChangeToVal = ReferencesSearch.search(parameter, LocalSearchScope(containingClass))
                .asIterable()
                .none {
                    val element = it.element
                    element != delegateExpression && !element.isMemberPropertyInitializer(containingClass) && element.hasAssignment()
                }
            val canRemoveVar = canChangeToVal && !isUsedForOtherClass

            val fixes = listOfNotNull(
                if (canChangeToVal) ChangeVariableMutabilityFix(parameter, makeVar = false).asQuickFix() else null,
                if (canRemoveVar) RemoveVarKeyword() else null,
            ).ifEmpty { return }

            holder.registerProblem(
                parameter,
                KotlinBundle.message("delegating.to.var.property.does.not.take.its.changes.into.account"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                *fixes.toTypedArray(),
            )
        })

    private fun PsiElement.isMemberPropertyInitializer(containingClass: KtClass): Boolean {
        val property = this.getStrictParentOfType<KtProperty>() ?: return false
        return property.initializer === this && property.containingClass() == containingClass
    }

    private fun PsiElement.hasAssignment(): Boolean {
        return when (val parent = this.parent) {
            is KtBinaryExpression -> parent.left == this && KtTokens.ALL_ASSIGNMENTS.contains(parent.operationToken)
            is KtPrefixExpression -> KtTokens.INCREMENT_AND_DECREMENT.contains(parent.operationToken)
            is KtPostfixExpression -> KtTokens.INCREMENT_AND_DECREMENT.contains(parent.operationToken)
            else -> false
        }
    }
}

private class RemoveVarKeyword : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.var.keyword.text")

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        (element as? KtParameter)?.valOrVarKeyword?.delete()
    }
}