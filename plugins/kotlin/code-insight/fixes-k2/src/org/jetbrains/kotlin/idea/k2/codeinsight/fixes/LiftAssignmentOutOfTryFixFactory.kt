// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.k2.refactoring.util.BranchedFoldingUtils
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.util.match

internal object LiftAssignmentOutOfTryFixFactory {

    val liftAssignmentOutOfTryFix = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ValReassignment ->
        listOfNotNull(
            diagnostic.psi.parentsWithSelf.match(
                KtExpression::class,
                KtBinaryExpression::class,
                KtBlockExpression::class,
                KtCatchClause::class,
                last = KtTryExpression::class,
            ).takeIf {
                BranchedFoldingUtils.getFoldableAssignmentsFromBranches(it).isNotEmpty()
            }?.let(::LiftAssignmentOutOfTryFix)
        )
    }

    private class LiftAssignmentOutOfTryFix(
        element: KtTryExpression,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtTryExpression, Unit>(element, Unit) {

        override fun getFamilyName(): String = KotlinBundle.message("lift.assignment.out.of.try.expression")

        override fun getPresentation(
            context: ActionContext,
            element: KtTryExpression,
        ): Presentation = Presentation.of(familyName)

        override fun invoke(
            actionContext: ActionContext,
            element: KtTryExpression,
            elementContext: Unit,
            updater: ModPsiUpdater,
        ) {
            BranchedFoldingUtils.tryFoldToAssignment(element)
        }
    }
}