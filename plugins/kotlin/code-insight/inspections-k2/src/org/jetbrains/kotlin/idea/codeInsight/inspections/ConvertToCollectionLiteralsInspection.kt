// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

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
import org.jetbrains.kotlin.idea.codeInsight.inspections.utils.TARGET_FUNCTION_FQ_NAMES
import org.jetbrains.kotlin.idea.codeInsight.inspections.utils.isCollectionLiteralSafeAsArgument
import org.jetbrains.kotlin.idea.codeInsight.inspections.utils.toCollectionLiteralString
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.KtWhenEntry
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
        return true
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
            is KtProperty, is KtPropertyAccessor, is KtNamedFunction -> {
                val property = when (parent) {
                    is KtProperty, is KtNamedFunction -> parent
                    else -> (parent as KtPropertyAccessor).parent as? KtProperty ?: return null
                }

                if (property.typeReference != null && fqName !in LIST_FQ_NAMES) {
                    expectedType ?: return null
                    if (expressionType !is KaClassType || expectedType !is KaClassType) return null
                    if (expressionType.classId != expectedType.classId) return null
                }
                if (property.typeReference == null) {
                    renderedType = renderExplicitTypeIfNeeded(element, fqName)
                }
            }

            is KtValueArgument -> {
                if ((fqName !in LIST_FQ_NAMES || element.typeArguments.isNotEmpty()) && !isCollectionLiteralSafeAsArgument(element, expressionType)) return null
                renderedType = renderExplicitTypeIfNeeded(element, fqName)
            }

            is KtBinaryExpression -> return null
            is KtBlockExpression -> if (parent.parent is KtFunctionLiteral) return null

            else -> {
                if (parent is KtWhenEntry && renderExplicitTypeIfNeeded(element, fqName) != null) return null
                if (fqName !in LIST_FQ_NAMES || parent is KtWhenEntry) {
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
            if (context.renderedType != null) {
                when(container) {
                    is KtPropertyAccessor -> { (container.parent as? KtProperty)?.setTypeReference(context.renderedType) }
                    is KtProperty, is KtNamedFunction -> container.setTypeReference(context.renderedType)
                }
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