// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
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
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.TARGET_FUNCTION_FQ_NAMES
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class ConvertCollectionLiteralToFunctionCallInspection :
    KotlinApplicableInspectionBase<KtCollectionLiteralExpression, ConvertCollectionLiteralToFunctionCallInspection.Context>() {

    internal data class Context(
        val functionName: Name,
        val collapseTarget: Name?,
    )

    override fun isAvailableForFile(file: PsiFile): Boolean =
        file is KtFile && file.languageVersionSettings.supportsFeature(LanguageFeature.CollectionLiterals)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ) = object : KtVisitorVoid() {
        override fun visitCollectionLiteralExpression(expression: KtCollectionLiteralExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtCollectionLiteralExpression): Boolean =
        element.getParentOfType<KtAnnotationEntry>(strict = true) == null

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtCollectionLiteralExpression): Context? {
        if (element.directDiagnostics(filter = KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS).isNotEmpty()) return null
        val resolvedSymbol = element.resolveSymbol() ?: return null
        if (!LITERAL_TO_FUNCTION_CANDIDATES.contains(resolvedSymbol.importableFqName)) return null

        val collapseTarget: Name? = run {
            val parent = element.parent as? KtDotQualifiedExpression ?: return@run null
            if (parent.receiverExpression != element) return@run null
            val callExpr = parent.selectorExpression as? KtCallExpression ?: return@run null
            if (callExpr.valueArguments.isNotEmpty()) return@run null

            // Restrict to stdlib collection conversion functions (kotlin or kotlin.collections)
            val callFqName = callExpr.resolveToCall()
                ?.successfulFunctionCallOrNull()
                ?.symbol?.callableId?.asSingleFqName() ?: return@run null
            val pkg = callFqName.parent()
            if (pkg != FqName("kotlin.collections") && pkg != FqName("kotlin")) return@run null

            // Return type of the whole chain expression must be a concrete class type
            val chainReturnType = parent.expressionType as? KaClassType ?: return@run null
            val chainClassId = chainReturnType.classId

            // Find which creation function in LITERAL_TO_FUNCTION_CANDIDATES returns this class type
            LITERAL_TO_FUNCTION_CANDIDATES.firstOrNull { candidateFqName ->
                findTopLevelCallables(candidateFqName.parent(), candidateFqName.shortName())
                    .filterIsInstance<KaNamedFunctionSymbol>()
                    .any { sym -> (sym.returnType as? KaClassType)?.classId == chainClassId }
            }?.shortName()
        }

        return Context(resolvedSymbol.name, collapseTarget)
    }

    override fun getApplicableRanges(element: KtCollectionLiteralExpression): List<TextRange> =
        ApplicabilityRange.union(element) { listOfNotNull(it.leftBracket, it.rightBracket) }

    override fun InspectionManager.createProblemDescriptor(
        element: KtCollectionLiteralExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor {
        if (context.collapseTarget != null) {
            val parent = element.parent as? KtDotQualifiedExpression
            if (parent != null) {
                val selector = parent.selectorExpression!!
                val selectorRange = TextRange(selector.startOffset - parent.startOffset, selector.endOffset - parent.startOffset)
                return createProblemDescriptor(
                    parent,
                    selectorRange,
                    KotlinBundle.message("inspection.kotlin.revert.to.collection.literals.display.name"),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    onTheFly,
                    ConvertLiteralFix(context.collapseTarget),
                    CollapseChainFix(context.collapseTarget),
                )
            }
        }
        return createProblemDescriptor(
            element,
            rangeInElement,
            KotlinBundle.message("inspection.kotlin.revert.to.collection.literals.display.name"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            onTheFly,
            ConvertLiteralFix(context.functionName),
        )
    }

    private class ConvertLiteralFix(private val name: Name?) : KotlinModCommandQuickFix<KtExpression>() {
        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.kotlin.revert.to.collection.literals.fix.name")

        override fun applyFix(project: Project, element: KtExpression, updater: ModPsiUpdater) {
            val factory = KtPsiFactory(project)
            when (element) {
                is KtCollectionLiteralExpression ->
                    element.replace(factory.createExpression("${name}(${element.text.drop(1).dropLast(1)})"))
                is KtDotQualifiedExpression -> {
                    val literal = element.receiverExpression as? KtCollectionLiteralExpression ?: return
                    element.replace(factory.createExpression("${name}(${literal.text.drop(1).dropLast(1)})"))
                }
            }
        }
    }

    private class CollapseChainFix(private val name: Name) : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {
        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.kotlin.remove.type.conversion.on.collection.literal.fix.name")

        override fun applyFix(project: Project, element: KtDotQualifiedExpression, updater: ModPsiUpdater) {
            val literal = element.receiverExpression as? KtCollectionLiteralExpression ?: return
            element.replace(KtPsiFactory(project).createExpression("${name}(${literal.text.drop(1).dropLast(1)})"))
        }
    }
}

private val LITERAL_TO_FUNCTION_CANDIDATES: List<FqName> = TARGET_FUNCTION_FQ_NAMES.filter {
    it != FqName("kotlin.collections.emptyList") && it != FqName("kotlin.collections.emptySet")
}
