// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.base.resources.BUNDLE
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.simpleNameExpressionVisitor

internal class AssignedValueIsNeverReadInspection : KotlinApplicableInspectionBase<KtSimpleNameExpression, Unit>() {
    data class Context(val hasSideEffects: Boolean)

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtSimpleNameExpression): Unit? {
        return element
            .diagnostics(KaDiagnosticCheckerFilter.ONLY_EXTENDED_CHECKERS)
            .any { it is KaFirDiagnostic.AssignedValueIsNeverRead }
            .asUnit
    }

    override fun InspectionManager.createProblemDescriptor(
        element: KtSimpleNameExpression,
        context: Unit,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor {
        val fixes = when (element.parent) {
            is KtUnaryExpression -> emptyArray()
            else -> arrayOf(RemoveRedundantAssignmentFix())
        }
        return createProblemDescriptor(
            /* psiElement = */ element,
            /* rangeInElement = */ rangeInElement,
            /* descriptionTemplate = */ KotlinBundle.message("assigned.value.is.never.read"),
            /* highlightType = */ ProblemHighlightType.LIKE_UNUSED_SYMBOL,
            /* onTheFly = */ false,
            /* ...fixes = */ *fixes,
        )
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = simpleNameExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    class RemoveRedundantAssignmentFix() : ModCommandQuickFix() {
        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.redundant.assignment")

        override fun perform(
            project: Project,
            descriptor: ProblemDescriptor,
        ): ModCommand {
            val binaryExpression = descriptor.psiElement.parent as? KtBinaryExpression ?: return ModCommand.nop()
            if (binaryExpression.right?.isPure() == true) {
                return ModCommand.psiUpdate(binaryExpression) { it.delete() }
            }
            val subActions = arrayOf(
                RemoveRedundantAssignmentSubFix.SideEffectAwareRemoveFix(binaryExpression),
                RemoveRedundantAssignmentSubFix.DeleteAssignmentCompletelyFix(binaryExpression),
            )
            return ModCommand.chooseAction(KotlinBundle.message("remove.redundant.assignment.title"), *subActions)
        }
    }

    sealed class RemoveRedundantAssignmentSubFix(
        element: KtBinaryExpression,
        private val messageKey: @NonNls @PropertyKey(resourceBundle = BUNDLE) String,
        private val action: (element: KtBinaryExpression) -> Unit,
    ) : PsiUpdateModCommandAction<KtBinaryExpression>(element) {

        class DeleteAssignmentCompletelyFix(element: KtBinaryExpression) :
            RemoveRedundantAssignmentSubFix(element, "delete.assignment.completely", { it.delete() })

        class SideEffectAwareRemoveFix(element: KtBinaryExpression) :
            RemoveRedundantAssignmentSubFix(element, "extract.side.effects", { it.replaceWithRight() })

        override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message(messageKey)

        override fun invoke(
            context: ActionContext,
            element: KtBinaryExpression,
            updater: ModPsiUpdater,
        ): Unit = action.invoke(element)
    }
}

private fun KtExpression.isPure(): Boolean = when (val expr = safeDeparenthesize()) {
    is KtStringTemplateExpression -> !expr.hasInterpolation()
    is KtConstantExpression -> true
    is KtSimpleNameExpression -> true
    is KtLambdaExpression -> true
    is KtIsExpression -> true
    is KtThisExpression -> true
    is KtCallExpression -> false
    else -> analyze(this) { evaluate() } != null
}
private fun KtBinaryExpression.replaceWithRight() {
    val right = this.right ?: return
    this.replace(right)
}
