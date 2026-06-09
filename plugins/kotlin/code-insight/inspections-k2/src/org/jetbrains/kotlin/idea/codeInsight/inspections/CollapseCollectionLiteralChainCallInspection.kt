// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeInsight.inspections.utils.LITERAL_TO_FUNCTION_CANDIDATES
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid

internal class CollapseCollectionLiteralChainCallInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, Name>() {

    override fun isAvailableForFile(file: PsiFile): Boolean =
        file is KtFile && file.languageVersionSettings.supportsFeature(LanguageFeature.CollectionLiterals)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        val parent = element.parent as? KtDotQualifiedExpression ?: return false
        if (parent.selectorExpression != element) return false
        val receiver = parent.receiverExpression
        if (receiver !is KtCollectionLiteralExpression &&
            (receiver as? KtParenthesizedExpression)?.expression !is KtCollectionLiteralExpression) return false
        return element.valueArguments.isEmpty()
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtCallExpression): Name? {
        val parent = element.parent as? KtDotQualifiedExpression ?: return null
        val receiver = parent.receiverExpression
        val literal = (receiver as? KtCollectionLiteralExpression)
            ?: (receiver as? KtParenthesizedExpression)?.expression as? KtCollectionLiteralExpression
            ?: return null

        if (literal.directDiagnostics(filter = KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS).isNotEmpty()) return null

        val callFqName = element.resolveToCall()
            ?.successfulFunctionCallOrNull()
            ?.symbol?.callableId?.asSingleFqName() ?: return null
        val pkg = callFqName.parent()
        if (pkg != FqName("kotlin.collections") && pkg != FqName("kotlin")) return null

        val chainReturnType = parent.expressionType as? KaClassType ?: return null
        val chainClassId = chainReturnType.classId

        return LITERAL_TO_FUNCTION_CANDIDATES.firstOrNull { candidateFqName ->
            findTopLevelCallables(candidateFqName.parent(), candidateFqName.shortName())
                .filterIsInstance<KaNamedFunctionSymbol>()
                .any { sym -> (sym.returnType as? KaClassType)?.classId == chainClassId }
        }?.shortName()
    }

    override fun getProblemDescription(element: KtCallExpression, context: Name): String =
        KotlinBundle.message("inspection.kotlin.remove.type.conversion.on.collection.literal.display.name")

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> = ApplicabilityRange.self(element)

    override fun createQuickFix(element: KtCallExpression, context: Name): KotlinModCommandQuickFix<KtCallExpression> =
        CollapseChainFix(context)

    private class CollapseChainFix(private val name: Name) : KotlinModCommandQuickFix<KtCallExpression>() {
        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.kotlin.remove.type.conversion.on.collection.literal.fix.name")

        override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
            val dotQualified = element.parent as? KtDotQualifiedExpression ?: return
            val receiver = dotQualified.receiverExpression
            val literal = (receiver as? KtCollectionLiteralExpression)
                ?: (receiver as? KtParenthesizedExpression)?.expression as? KtCollectionLiteralExpression
                ?: return
            dotQualified.replace(KtPsiFactory(project).createExpression("${name}(${literal.text.drop(1).dropLast(1)})"))
        }
    }
}
