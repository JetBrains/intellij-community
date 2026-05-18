// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.getTopmostParenthesizedExpressionOrSelf
import org.jetbrains.kotlin.idea.codeinsight.utils.setTypeReference
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.TARGET_FUNCTION_FQ_NAMES
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.isCollectionLiteralSafeAsArgument
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils.toCollectionLiteralString
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.types.Variance

internal class ConvertToCollectionLiteralsInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, ConvertToCollectionLiteralsInspection.Context>() {

    internal data class Context(val renderedType: String? = null)

    override fun isAvailableForFile(file: PsiFile): Boolean =
        file is KtFile && file.languageVersionSettings.supportsFeature(LanguageFeature.CollectionLiterals)

    override fun getProblemDescription(
        element: KtCallExpression,
        context: Context
    ): @InspectionMessage String = KotlinBundle.message("inspection.kotlin.convert.to.collection.literals.display.name")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ) = object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        val calleeExpression = element.calleeExpression as? KtNameReferenceExpression ?: return false
        if (calleeExpression.getReferencedNameAsName() !in TARGET_FUNCTION_SHORT_NAMES) return false
        if (element.valueArguments.any { it.getArgumentName() != null || it.getSpreadElement() != null }) return false
        val parent = element.parent
        return !(parent is KtDotQualifiedExpression && parent.receiverExpression == element)
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        val callSymbol = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val fqName = callSymbol.symbol.callableId?.asSingleFqName() ?: return null
        if (fqName !in TARGET_FUNCTION_FQ_NAMES) return null

        val expressionType = element.expressionType ?: return null
        val expectedType = element.expectedType
        val parent = element.getTopmostParenthesizedExpressionOrSelf().parent

        var renderedType: String? = null
        when (parent) {
            is KtProperty -> {
                if (parent.typeReference != null && fqName !in LIST_FQ_NAMES) {
                    expectedType ?: return null
                    if (expressionType !is KaClassType || expectedType !is KaClassType) return null
                    if (expressionType.classId != expectedType.classId) return null
                }
                if (parent.typeReference == null) {
                    renderedType = renderExplicitTypeIfNeeded(element, fqName)
                }
            }

            is KtNamedFunction -> renderedType = renderExplicitTypeIfNeeded(element, fqName)

            is KtValueArgument -> {
                if (!isCollectionLiteralSafeAsArgument(element, expressionType)) return null
                renderedType = renderExplicitTypeIfNeeded(element, fqName)
            }

            else -> {
                if (fqName !in LIST_FQ_NAMES) {
                    if (expectedType == null || !expressionType.semanticallyEquals(expectedType))
                        return null
                }
            }
        }
        return Context(renderedType)
    }


    @OptIn(KaExperimentalApi::class)
    private fun KaSession.renderExplicitTypeIfNeeded(element: KtCallExpression, fqName: FqName): String? {
        if (fqName != LIST_LITERAL || element.typeArguments.isNotEmpty()) {
            return element.expressionType!!.render(position = Variance.OUT_VARIANCE)
        }
        return null
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRange.single(element) { it.calleeExpression }

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {
        override fun getFamilyName(): String =
            KotlinBundle.message("inspection.kotlin.convert.to.collection.literals.fix.name")

        override fun applyFix(project: Project, element: KtCallExpression, updater: ModPsiUpdater) {
            val directParent = element.parent
            val container = if (directParent is KtParenthesizedExpression) directParent.parent else directParent
            if ((container is KtProperty || container is KtNamedFunction) && context.renderedType != null) {
                container.setTypeReference(context.renderedType)
            }

            val collectionLiteralString = element.toCollectionLiteralString() ?: return
            element.replace(
                KtPsiFactory(project).createExpression(collectionLiteralString)
            )
        }
    }

    private val LIST_LITERAL = FqName("kotlin.collections.listOf")

    private val LIST_FQ_NAMES: Set<FqName> = setOf(
        LIST_LITERAL,
        FqName("kotlin.collections.emptyList"),
    )

    private val TARGET_FUNCTION_SHORT_NAMES: Set<Name> = TARGET_FUNCTION_FQ_NAMES.mapTo(HashSet()) { it.shortName() }
}