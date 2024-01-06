// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedElementSelector

private val FILTER_IS_INSTANCE_CALLABLE_ID = CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, Name.identifier("filterIsInstance"))

internal class FilterIsInstanceCallWithClassLiteralArgumentInspection :
    AbstractKotlinApplicableInspection<KtCallExpression>(), CleanupLocalInspectionTool {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }
    }
    override fun getProblemDescription(element: KtCallExpression): String =
        KotlinBundle.message("inspection.filter.is.instance.call.with.class.literal.argument.display.name")

    override fun getActionFamilyName(): String =
        KotlinBundle.message("inspection.filter.is.instance.call.with.class.literal.argument.quick.fix.text")

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtCallExpression> =
        ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtCallExpression): Boolean =
        element.calleeExpression?.text == "filterIsInstance" && element.valueArguments.singleOrNull()?.isClassLiteral() == true

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtCallExpression): Boolean {
        if (element.resolveToFunctionSymbol()?.callableIdIfNonLocal != FILTER_IS_INSTANCE_CALLABLE_ID) return false

        val classLiteral = element.valueArguments.singleOrNull()?.classLiteral() ?: return false
        val classNameReference = classLiteral.receiverExpression?.getQualifiedElementSelector() ?: return false
        val classSymbol = classNameReference.resolveToClassSymbol() ?: return false
        return classSymbol.typeParameters.isEmpty()
    }

    override fun apply(element: KtCallExpression, project: Project, updater: ModPsiUpdater) {
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

private fun KtValueArgument.isClassLiteral(): Boolean =
    classLiteral() != null

private fun KtValueArgument.classLiteral(): KtClassLiteralExpression? =
    (getArgumentExpression() as? KtDotQualifiedExpression)?.receiverExpression as? KtClassLiteralExpression

context(KtAnalysisSession)
private fun KtElement.resolveToClassSymbol(): KtNamedClassOrObjectSymbol? =
  mainReference?.resolveToSymbol() as? KtNamedClassOrObjectSymbol

context(KtAnalysisSession)
private fun KtCallExpression.resolveToFunctionSymbol(): KtFunctionSymbol? =
    calleeExpression?.mainReference?.resolveToSymbol() as? KtFunctionSymbol
