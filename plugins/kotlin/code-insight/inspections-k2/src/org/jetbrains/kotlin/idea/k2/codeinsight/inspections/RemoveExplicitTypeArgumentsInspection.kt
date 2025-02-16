// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.RemoveExplicitTypeArgumentsUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.canExplicitTypeBeRemoved
import org.jetbrains.kotlin.idea.k2.refactoring.util.areTypeArgumentsRedundant
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal class RemoveExplicitTypeArgumentsInspection : KotlinApplicableInspectionBase<KtTypeArgumentList, Unit>() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = object : KtVisitorVoid() {
        override fun visitTypeArgumentList(typeArgumentList: KtTypeArgumentList) {
            visitTargetElement(typeArgumentList, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtTypeArgumentList): Boolean {
        val callExpression = element.parent as? KtCallExpression ?: return false
        return RemoveExplicitTypeArgumentsUtils.isApplicableByPsi(callExpression)
    }

    override fun KaSession.prepareContext(element: KtTypeArgumentList): Unit? = areTypeArgumentsRedundant(element).asUnit

    override fun InspectionManager.createProblemDescriptor(
        element: KtTypeArgumentList,
        context: Unit,
        rangeInElement: TextRange?,
        onTheFly: Boolean
    ): ProblemDescriptor = createProblemDescriptor(
        /* psiElement = */ element,
        /* rangeInElement = */ rangeInElement,
        /* descriptionTemplate = */ KotlinBundle.message("explicit.type.arguments.can.be.inferred"),
        /* highlightType = */ ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        /* onTheFly = */ onTheFly,
        /* ...fixes = */ *createQuickFixes(element).toTypedArray(),
    )

    private fun createQuickFixes(
        element: KtTypeArgumentList,
    ): Collection<KotlinModCommandQuickFix<KtTypeArgumentList>> = listOfNotNull(
        createRemoveExplicitTypeArgumentsFix(),
        createRemoveExplicitTypeFix(element),
    )

    private fun createRemoveExplicitTypeArgumentsFix(): KotlinModCommandQuickFix<KtTypeArgumentList> =
        object : KotlinModCommandQuickFix<KtTypeArgumentList>() {
            override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("remove.explicit.type.arguments")

            override fun applyFix(
                project: Project,
                element: KtTypeArgumentList,
                updater: ModPsiUpdater,
            ): Unit = RemoveExplicitTypeArgumentsUtils.applyTo(element)
        }

    private fun createRemoveExplicitTypeFix(element: KtTypeArgumentList): KotlinModCommandQuickFix<KtTypeArgumentList>? {
        val declaration = element.getStrictParentOfType<KtCallableDeclaration>()
        val declarationName = declaration?.name ?: return null
        if (!canExplicitTypeBeRemoved(declaration)) return null
        return object : KotlinModCommandQuickFix<KtTypeArgumentList>() {
            override fun getFamilyName(): @IntentionFamilyName String =
                KotlinBundle.message("remove.explicit.type.specification.from.0", declarationName)

            override fun applyFix(
                project: Project,
                element: KtTypeArgumentList,
                updater: ModPsiUpdater,
            ) {
                val declaration = element.getStrictParentOfType<KtCallableDeclaration>() ?: return
                declaration.typeReference = null
            }
        }
    }
}
