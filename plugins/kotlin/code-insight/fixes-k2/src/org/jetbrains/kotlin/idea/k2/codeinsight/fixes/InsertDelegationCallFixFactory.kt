// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaErrorCallInfo
import org.jetbrains.kotlin.analysis.api.calls.KtSuccessCallInfo
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.allConstructors
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal object InsertDelegationCallFixFactory {

    val explicitDelegationCallRequiredSuper = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ExplicitDelegationCallRequired ->
        createQuickFix(diagnostic, isThis = false)
    }

    val explicitDelegationCallRequiredThis = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ExplicitDelegationCallRequired ->
        createQuickFix(diagnostic, isThis = true)
    }

    val primaryConstructorDelegationCallExpected = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.PrimaryConstructorDelegationCallExpected ->
        val secondaryConstructor = diagnostic.psi.getParentOfType<KtSecondaryConstructor>(
            strict = false,
            KtClassBody::class.java,
        ) ?: return@ModCommandBased emptyList()

        val containingClass = secondaryConstructor.getContainingClassOrObject()
        if (containingClass.allConstructors.count() <= 1 || !secondaryConstructor.hasImplicitDelegationCall()) {
            return@ModCommandBased emptyList()
        }

        listOf(InsertDelegationCallFix(element = secondaryConstructor, isThis = true))
    }

    private fun createQuickFix(diagnostic: KaFirDiagnostic.ExplicitDelegationCallRequired, isThis: Boolean): List<InsertDelegationCallFix> {
        val secondaryConstructor = diagnostic.psi.getParentOfType<KtSecondaryConstructor>(
            strict = false,
            KtClassBody::class.java,
        ) ?: return emptyList()

        if (!secondaryConstructor.hasImplicitDelegationCall()) return emptyList()
        val klass = secondaryConstructor.getContainingClassOrObject() as? KtClass ?: return emptyList()
        if (klass.hasPrimaryConstructor()) return emptyList()

        return listOf(InsertDelegationCallFix(element = secondaryConstructor, isThis))
    }

    private class InsertDelegationCallFix(
        element: KtSecondaryConstructor,
        private val isThis: Boolean,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtSecondaryConstructor, Unit>(element, Unit) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtSecondaryConstructor,
            elementContext: Unit,
            updater: ModPsiUpdater,
        ) {
            val newDelegationCall = element.replaceImplicitDelegationCallWithExplicit(isThis)

            analyze(newDelegationCall) {
                val resolvedCall = newDelegationCall.resolveCallOld()

                // If the new delegation call does not contain errors and there is no cycle in the delegation call chain,
                // do not move the caret.
                if (resolvedCall is KtSuccessCallInfo && element.valueParameters.any { !it.hasDefaultValue() }) return
            }
            val leftParOffset = newDelegationCall.valueArgumentList!!.leftParenthesis!!.textOffset
            updater.moveCaretTo(leftParOffset + 1)
        }

        override fun getActionName(
            actionContext: ActionContext,
            element: KtSecondaryConstructor,
            elementContext: Unit,
        ): String {
            val keyword = if (isThis) "this" else "super"
            return KotlinBundle.message("fix.insert.delegation.call", keyword)
        }

        override fun getFamilyName(): String {
            return KotlinBundle.message("insert.explicit.delegation.call")
        }
    }
}
