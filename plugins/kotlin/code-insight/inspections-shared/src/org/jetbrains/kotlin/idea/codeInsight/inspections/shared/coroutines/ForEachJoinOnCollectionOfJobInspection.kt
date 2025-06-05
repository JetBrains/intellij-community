// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.CoroutinesIds.JOB_JOIN_ID
import org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines.CoroutinesIds.JOIN_ALL_ID
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.refactoring.singleLambdaArgumentExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.callExpressionVisitor

/**
 * @see MapAwaitOnCollectionOfDeferredInspection for a similar inspection for `map { it.await() }` calls.
 */
internal class ForEachJoinOnCollectionOfJobInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, Unit>() {
    override fun getProblemDescription(
        element: KtCallExpression,
        context: Unit
    ): @InspectionMessage String {
        return KotlinBundle.message("inspection.foreach.join.on.collection.of.job.description")
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = callExpressionVisitor { callExpression ->
        visitTargetElement(callExpression, holder, isOnTheFly)
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Unit? {
        val lambdaArgument = element.singleLambdaArgumentExpression() ?: return null

        if (!isIterableForEachFunctionCall(element)) return null
        if (!isLambdaWithSingleReturnedCallOnSingleParameter(lambdaArgument, JOB_JOIN_ID)) return null

        return Unit
    }

    override fun createQuickFix(
        element: KtCallExpression,
        context: Unit
    ): KotlinModCommandQuickFix<KtCallExpression> {
        return object : KotlinModCommandQuickFix<KtCallExpression>() {
            override fun getFamilyName(): @IntentionFamilyName String {
                return KotlinBundle.message("inspection.foreach.join.on.collection.of.job.replace.with.joinAll")
            }

            override fun applyFix(
                project: Project,
                element: KtCallExpression,
                updater: ModPsiUpdater
            ) {
                val alreadyImportedByStarImport = isPackageImportedByStarImport(element.containingKtFile, JOIN_ALL_ID.packageName)

                if (!alreadyImportedByStarImport) {
                    element.containingKtFile.addImport(JOIN_ALL_ID.asSingleFqName())
                }

                element.replace(KtPsiFactory(project).createExpression("${JOIN_ALL_ID.callableName}()"))
            }
        }
    }
}
