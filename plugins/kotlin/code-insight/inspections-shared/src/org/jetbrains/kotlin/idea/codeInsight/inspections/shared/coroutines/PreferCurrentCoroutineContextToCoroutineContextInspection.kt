// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.coroutines

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

internal class PreferCurrentCoroutineContextToCoroutineContextInspection : KotlinApplicableInspectionBase.Simple<KtExpression, Unit>() {

    override fun getProblemDescription(
        element: KtExpression,
        context: Unit
    ): @InspectionMessage String {
        return KotlinBundle.message("inspection.prefer.current.coroutine.context.to.coroutine.context.inspection.description")
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = expressionVisitor { expression ->
        visitTargetElement(expression, holder, isOnTheFly)
    }

    override fun KaSession.prepareContext(element: KtExpression): Unit? {
        val nameReferenceExpression = when (element) {
            is KtNameReferenceExpression if (element.getQualifiedExpressionForSelector() == null) -> element
            is KtDotQualifiedExpression -> element.selectorExpression as? KtNameReferenceExpression
            else -> null
        } ?: return null

        if (!isCoroutineContextFunctionAccess(nameReferenceExpression)) {
            return null
        }

        if (!isCurrentCoroutineContextFunctionPresent()) {
            return null
        }

        return Unit
    }

    private fun KaSession.isCoroutineContextFunctionAccess(reference: KtNameReferenceExpression): Boolean {
        return reference.getReferencedNameAsName() == KOTLIN_COROUTINES_CONTEXT_ID.callableName &&
                (reference.mainReference.resolveToSymbol() as? KaPropertySymbol)?.callableId == KOTLIN_COROUTINES_CONTEXT_ID
    }

    private fun KaSession.isCurrentCoroutineContextFunctionPresent(): Boolean {
        return CoroutinesIds.CURRENT_COROUTINE_CONTEXT_ID.canBeResolved()
    }

    override fun createQuickFix(
        element: KtExpression,
        context: Unit
    ): KotlinModCommandQuickFix<KtExpression> {
        return object : KotlinModCommandQuickFix<KtExpression>() {
            override fun getFamilyName(): @IntentionFamilyName String {
                return KotlinBundle.message("inspection.prefer.current.coroutine.context.to.coroutine.context.inspection.fix")
            }

            override fun applyFix(
                project: Project,
                element: KtExpression,
                updater: ModPsiUpdater
            ) {
                val currentCoroutineContextCall = KtPsiFactory(project).createExpression(
                    "${CoroutinesIds.CURRENT_COROUTINE_CONTEXT_ID.asSingleFqName()}()"
                )

                val replaced = element.replaced(currentCoroutineContextCall)

                ShortenReferencesFacility.getInstance().shorten(replaced)
            }
        }
    }
}

private val KOTLIN_COROUTINES_CONTEXT_ID = CallableId(FqName("kotlin.coroutines"), Name.identifier("coroutineContext"))
