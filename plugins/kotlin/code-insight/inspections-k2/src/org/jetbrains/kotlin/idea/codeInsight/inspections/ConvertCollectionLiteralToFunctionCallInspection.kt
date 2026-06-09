// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

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
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeInsight.inspections.utils.LITERAL_TO_FUNCTION_CANDIDATES
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

internal class ConvertCollectionLiteralToFunctionCallInspection :
    KotlinApplicableInspectionBase<KtCollectionLiteralExpression, ConvertCollectionLiteralToFunctionCallInspection.Context>() {

    internal data class Context(val functionName: Name)

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
        return Context(resolvedSymbol.name)
    }

    override fun getApplicableRanges(element: KtCollectionLiteralExpression): List<TextRange> =
        ApplicabilityRange.union(element) { listOfNotNull(it.leftBracket, it.rightBracket) }

    override fun InspectionManager.createProblemDescriptor(
        element: KtCollectionLiteralExpression,
        context: Context,
        rangeInElement: TextRange?,
        onTheFly: Boolean,
    ): ProblemDescriptor = createProblemDescriptor(
        element,
        rangeInElement,
        KotlinBundle.message("inspection.kotlin.revert.to.collection.literals.display.name"),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        onTheFly,
        ConvertLiteralFix(context.functionName),
    )

    private class ConvertLiteralFix(private val name: Name) : KotlinModCommandQuickFix<KtCollectionLiteralExpression>() {
        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.kotlin.revert.to.collection.literals.fix.name")

        override fun applyFix(project: Project, element: KtCollectionLiteralExpression, updater: ModPsiUpdater) {
            element.replace(KtPsiFactory(project).createExpression("${name}(${element.text.drop(1).dropLast(1)})"))
        }
    }
}
