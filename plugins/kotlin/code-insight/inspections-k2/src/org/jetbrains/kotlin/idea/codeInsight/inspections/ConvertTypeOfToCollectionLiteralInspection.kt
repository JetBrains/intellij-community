// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.getTopmostParenthesizedExpressionOrSelf
import org.jetbrains.kotlin.idea.codeinsight.utils.setTypeReference
import org.jetbrains.kotlin.idea.codeInsight.inspections.utils.isCollectionLiteralSafeAsArgument
import org.jetbrains.kotlin.idea.codeInsight.inspections.utils.toCollectionLiteralString
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.types.Variance

internal class ConvertTypeOfToCollectionLiteralInspection :
    KotlinApplicableInspectionBase.Simple<KtDotQualifiedExpression, ConvertTypeOfToCollectionLiteralInspection.Context>() {

    data class Context(val typeText: String)

    override fun getProblemDescription(
        element: KtDotQualifiedExpression,
        context: Context,
    ): @InspectionMessage String =
        KotlinBundle.message("inspection.kotlin.convert.type.of.to.collection.literal.display.name")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun isAvailableForFile(file: PsiFile): Boolean {
        return file is KtFile && file.languageVersionSettings.supportsFeature(LanguageFeature.CollectionLiterals)
    }

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        if (element.receiverExpression !is KtNameReferenceExpression) return false
        val callExpr = element.selectorExpression as? KtCallExpression ?: return false
        val callee = callExpr.calleeExpression as? KtNameReferenceExpression ?: return false
        if (callee.getReferencedName() != "of") return false
        val parent = element.parent
        if (parent is KtDotQualifiedExpression && parent.receiverExpression == element) return false
        return true
    }

    override fun getApplicableRanges(element: KtDotQualifiedExpression) = ApplicabilityRange.self(element)

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Context? {
        val callExpr = element.selectorExpression as? KtCallExpression ?: return null
        val call = callExpr.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val functionSymbol = call.symbol as? KaNamedFunctionSymbol ?: return null

        if (functionSymbol.name.asString() != "of" || !functionSymbol.isOperator) return null
        val expressionType = element.expressionType ?: return null
        val parent = element.getTopmostParenthesizedExpressionOrSelf().parent
        if (parent is KtValueArgument && !isCollectionLiteralSafeAsArgument(callExpr, expressionType)) return null
        if (isTypeChanged(parent, expressionType)) return null
        val typeText = expressionType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.OUT_VARIANCE)

        return Context(typeText)
    }

    override fun createQuickFix(
        element: KtDotQualifiedExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtDotQualifiedExpression> = object : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("inspection.kotlin.convert.type.of.to.collection.literal.fix.name")

        override fun applyFix(project: Project, element: KtDotQualifiedExpression, updater: ModPsiUpdater) {
            val callExpr = element.selectorExpression as? KtCallExpression ?: return
            val literalText = callExpr.toCollectionLiteralString() ?: return

            val psiFactory = KtPsiFactory(project)
            val collectionLiteral = psiFactory.createExpression(literalText) as KtCollectionLiteralExpression

            (element.getTopmostParenthesizedExpressionOrSelf().parent as? KtCallableDeclaration)
                ?.takeIf { it.typeReference == null }
                ?.setTypeReference(context.typeText)

            element.replace(collectionLiteral)
        }
    }

    private fun KaSession.isTypeChanged(element: PsiElement, expressionType: KaType): Boolean =
        (element as? KtCallableDeclaration)
            ?.typeReference
            ?.type
            ?.semanticallyEquals(expressionType) == false
}
