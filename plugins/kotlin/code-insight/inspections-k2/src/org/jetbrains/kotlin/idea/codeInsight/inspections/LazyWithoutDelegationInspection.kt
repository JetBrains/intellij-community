// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.removeUnnecessaryParentheses
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.collectReferencesInFile
import org.jetbrains.kotlin.idea.codeinsight.utils.isInitializedByLazy
import org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.getOutermostParenthesizedExpressionOrThis
import org.jetbrains.kotlin.idea.util.hasJvmFieldAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.propertyVisitor
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import kotlin.sequences.last

/**
 * Finds non-delegated properties that are initialized with a value of type `kotlin.Lazy<...>`.
 * If property is used exclusively through its `value` property, it can be converted to a delegated property.
 * Only local variables and private properties are checked.
 */
internal class LazyWithoutDelegationInspection :
    KotlinApplicableInspectionBase.Simple<KtProperty, LazyWithoutDelegationInspection.Context>() {

    /**
     * @param lazyValueAccessors is a list of qualified expressions of the form `propertyName.value`
     * where the property has lazy initialization
     */
    data class Context(val lazyValueAccessors: List<KtDotQualifiedExpression>)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean
    ): KtVisitor<*, *> = propertyVisitor { property ->
        visitTargetElement(property, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtProperty): Boolean {
        if (element.isVar) return false
        if (!element.isLocal && !element.isPrivate()) return false
        if (element.hasDelegate()) return false
        if (element.typeReference != null) return false
        if (element.initializer == null) return false
        if (element.annotationEntries.isNotEmpty()) return false
        return true
    }

    override fun getApplicableRanges(element: KtProperty): List<TextRange> = ApplicabilityRange.single(element) { it.equalsToken }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtProperty): Context? {
        if (!element.isInitializedByLazy()) return null
        if (element.hasJvmFieldAnnotation()) return null
        val lazyValueAccessors = collectLazyValueAccessors(element) ?: return null

        return Context(lazyValueAccessors)
    }

    override fun getProblemDescription(
        element: KtProperty,
        context: Context
    ): @InspectionMessage String = KotlinBundle.message("inspection.lazy.without.delegation.display.name")

    override fun createQuickFix(element: KtProperty, context: Context): KotlinModCommandQuickFix<KtProperty> =
        object : KotlinModCommandQuickFix<KtProperty>() {
            override fun getFamilyName(): String = KotlinBundle.message("inspection.lazy.without.delegation.fix.name")

            override fun applyFix(project: Project, element: KtProperty, updater: ModPsiUpdater) {
                val factory = KtPsiFactory(element.project)
                val writableLazyValues = context.lazyValueAccessors.map { updater.getWritable(it) }

                val byKeyword = factory.createExpression(KtTokens.BY_KEYWORD.value)
                element.equalsToken?.replace(byKeyword)

                writableLazyValues.forEach {
                    val replaced = it.replace(factory.createExpression(it.receiverExpression.text))
                    if (replaced is KtParenthesizedExpression && KtPsiUtil.areParenthesesUseless(replaced)) {
                        replaced.removeUnnecessaryParentheses()
                    }
                }
            }
        }

    context(_: KaSession)
    private fun collectLazyValueAccessors(property: KtProperty): List<KtDotQualifiedExpression>? {
        val refs = property.collectReferencesInFile()
        return refs.map { ref -> findLazyValueAccessor(ref) ?: return null }
    }

    context(_: KaSession)
    private fun findLazyValueAccessor(ref: KtNameReferenceExpression): KtDotQualifiedExpression? {
        val propertyAccess = generateSequence(ref.getOutermostParenthesizedExpressionOrThis()) { expression ->
            val parent = expression.getQualifiedExpressionForSelector() as? KtDotQualifiedExpression
            parent?.getOutermostParenthesizedExpressionOrThis()
        }.last()

        val parent = propertyAccess.parent as? KtDotQualifiedExpression ?: return null
        if (parent.receiverExpression != propertyAccess) return null
        val isLazyValueCall = parent.selectorExpression
                    ?.resolveToCall()
                    ?.singleVariableAccessCall()
                    ?.symbol
                    ?.callableId == StandardKotlinNames.Lazy.lazyValue

        return parent.takeIf { isLazyValueCall }
    }
}
