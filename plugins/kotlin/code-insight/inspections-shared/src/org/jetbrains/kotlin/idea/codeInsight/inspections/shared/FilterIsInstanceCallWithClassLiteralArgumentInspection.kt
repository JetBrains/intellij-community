// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector

private val FILTER_IS_INSTANCE_CALLABLE_ID = CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, Name.identifier("filterIsInstance"))

internal class FilterIsInstanceCallWithClassLiteralArgumentInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, Unit>(),
                                                                        CleanupLocalInspectionTool {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(
        element: KtCallExpression,
        context: Unit,
    ): String = KotlinBundle.message("inspection.filter.is.instance.call.with.class.literal.argument.display.name")

    override fun isApplicableByPsi(element: KtCallExpression): Boolean =
        element.calleeExpression?.text == "filterIsInstance" && element.valueArguments.singleOrNull()?.isClassLiteral() == true

    override fun KaSession.prepareContext(element: KtCallExpression): Unit? {
        if (element.resolveToFunctionSymbol()?.callableId != FILTER_IS_INSTANCE_CALLABLE_ID) return null

        return element.valueArguments
            .singleOrNull()
            ?.classLiteral()
            ?.receiverExpression
            ?.getQualifiedElementSelector()
            ?.resolveToClassSymbol()
            ?.typeParameters
            ?.isEmpty()
            ?.asUnit
    }

    override fun createQuickFix(
        element: KtCallExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.filter.is.instance.call.with.class.literal.argument.quick.fix.text")

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            val callee = element.calleeExpression ?: return
            val argument = element.valueArguments.singleOrNull() ?: return
            val typeName = argument.classLiteral()?.receiverExpression?.text ?: return

            element.typeArgumentList?.delete()
            val typeArguments = KtPsiFactory(project).createTypeArguments("<$typeName>")
            val newTypeArguments = element.addAfter(typeArguments, callee) as? KtElement ?: return
            ShortenReferencesFacility.getInstance().shorten(newTypeArguments)
            element.valueArgumentList?.removeArgument(argument)
        }
    }
}

private fun KtValueArgument.isClassLiteral(): Boolean =
    classLiteral() != null

private fun KtValueArgument.classLiteral(): KtClassLiteralExpression? =
    (getArgumentExpression() as? KtDotQualifiedExpression)?.receiverExpression as? KtClassLiteralExpression

context(_: KaSession)
private fun KtElement.resolveToClassSymbol(): KaNamedClassSymbol? =
    mainReference?.resolveToSymbol() as? KaNamedClassSymbol

context(_: KaSession)
private fun KtCallExpression.resolveToFunctionSymbol(): KaNamedFunctionSymbol? =
    calleeExpression?.mainReference?.resolveToSymbol() as? KaNamedFunctionSymbol
