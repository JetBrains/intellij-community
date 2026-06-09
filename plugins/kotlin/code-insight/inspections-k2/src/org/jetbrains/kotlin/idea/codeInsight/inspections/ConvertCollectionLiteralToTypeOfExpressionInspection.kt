// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.getTopmostParenthesizedExpressionOrSelf
import org.jetbrains.kotlin.idea.codeinsight.utils.removeDeclarationTypeReference
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal class ConvertCollectionLiteralToTypeOfExpressionInspection :
    KotlinApplicableInspectionBase.Simple<KtCollectionLiteralExpression, ConvertCollectionLiteralToTypeOfExpressionInspection.Context>() {

    data class Context(val className: String, val shouldRemoveTypeRef: Boolean)

    override fun getProblemDescription(
        element: KtCollectionLiteralExpression,
        context: Context,
    ): @InspectionMessage String =
        KotlinBundle.message("inspection.kotlin.convert.collection.literal.to.type.of.display.name", "${context.className}.of")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object : KtVisitorVoid() {
        override fun visitCollectionLiteralExpression(expression: KtCollectionLiteralExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun isAvailableForFile(file: PsiFile): Boolean {
        return file is KtFile && file.languageVersionSettings.supportsFeature(LanguageFeature.CollectionLiterals)
    }

    override fun isApplicableByPsi(element: KtCollectionLiteralExpression): Boolean =
        element.getParentOfType<KtAnnotationEntry>(strict = true) == null

    override fun getApplicableRanges(element: KtCollectionLiteralExpression) = ApplicabilityRange.self(element)

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtCollectionLiteralExpression): Context? {
        if (element.directDiagnostics(filter = KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS).isNotEmpty()) return null
        val classSymbol = findClassSymbolForLiteral(element) ?: return null
        val className = classSymbol.name.asString()

        val shouldRemoveTypeRef = run {
            if (element.innerExpressions.isEmpty()) return@run false
            val parent = element.getTopmostParenthesizedExpressionOrSelf().parent
            if (parent !is KtProperty && parent !is KtNamedFunction) return@run false
            if (parent.typeReference == null) return@run false
            val declaredType = parent.typeReference!!.type as? KaClassType ?: return@run false
            declaredType.symbol == classSymbol
        }

        return Context(className, shouldRemoveTypeRef)
    }

    override fun createQuickFix(
        element: KtCollectionLiteralExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtCollectionLiteralExpression> = object : KotlinModCommandQuickFix<KtCollectionLiteralExpression>() {

        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("inspection.kotlin.convert.collection.literal.to.type.of.fix.name", "${context.className}.of")

        override fun applyFix(project: Project, element: KtCollectionLiteralExpression, updater: ModPsiUpdater) {
            val argsText = element.text.drop(1).dropLast(1)
            val expression = KtPsiFactory(project).createExpression("${context.className}.of($argsText)")

            val property = element.getTopmostParenthesizedExpressionOrSelf().parent
            element.replace(expression)

            if (context.shouldRemoveTypeRef && property != null && property is KtCallableDeclaration) {
                property.removeDeclarationTypeReference()
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.findClassSymbolForLiteral(element: KtCollectionLiteralExpression): KaNamedClassSymbol? {
        val resolvedSymbol = element.resolveSymbol()
        if (resolvedSymbol != null && resolvedSymbol.name.asString() == "of" && resolvedSymbol.isOperator) {
            val companion = resolvedSymbol.containingSymbol as? KaClassSymbol
            if (companion?.classKind == KaClassKind.COMPANION_OBJECT) {
                return companion.containingSymbol as? KaNamedClassSymbol
            }
        }

        val classType = element.expressionType as? KaClassType ?: return null
        val classSymbol = classType.symbol as? KaNamedClassSymbol ?: return null
        val companion = classSymbol.companionObject ?: return null
        val hasOfOperator = companion.declaredMemberScope.callables
            .filterIsInstance<KaNamedFunctionSymbol>()
            .any { it.name.asString() == "of" && it.isOperator }
        return if (hasOfOperator) classSymbol else null
    }
}
