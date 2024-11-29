/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaSimpleFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ReplaceIsEmptyWithIfEmptyInspection.Replacement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtBreakExpression
import org.jetbrains.kotlin.psi.KtContinueExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpressionWithLabel
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.blockExpressionsOrSingle
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

/**
 * This inspection detects `if` statements containing only a single call to
 * `isEmpty`/`isBlank`/`isNotEmpty`/`isNotBlank` extensions on some collection
 * which could be turned into a more simple call to `ifEmpty`/`ifBlank`.
 *
 * For this purpose, the `if` construction should have only two branches,
 * one of them returning just the collection receiver the extension is called on.
 *
 * Additionally, inside branches there should be no non-local `continue` and `break` statements.
 *
 * Before quick fix:
 *
 * ```kotlin
 *  if (list.isEmpty()) {
 *          ^^^
 *      Fix: Replace with 'ifEmpty {...}'
 *
 *      listOf(1, 2, 3)
 *  } else {
 *      list
 *  }
 * ```
 *
 * After quick fix:
 *
 * ```kotlin
 *  list.ifEmpty {
 *      listOf(1, 2, 3)
 *  }
 * ```
 *
 */
internal class ReplaceIsEmptyWithIfEmptyInspection : KotlinApplicableInspectionBase.Simple<KtIfExpression, Replacement>() {
    private val replacements: Map<CallableId, Replacement> = listOf<Replacement>(
        Replacement(CallableId(StandardClassIds.Collection, Name.identifier("isEmpty")), "ifEmpty"),
        Replacement(CallableId(StandardClassIds.List, Name.identifier("isEmpty")), "ifEmpty"),
        Replacement(CallableId(StandardClassIds.Set, Name.identifier("isEmpty")), "ifEmpty"),
        Replacement(CallableId(StandardClassIds.Map, Name.identifier("isEmpty")), "ifEmpty"),
        Replacement(CallableId(StandardClassIds.BASE_TEXT_PACKAGE, Name.identifier("isEmpty")), "ifEmpty"),
        Replacement(CallableId(StandardClassIds.BASE_TEXT_PACKAGE, Name.identifier("isBlank")), "ifBlank"),
        Replacement(CallableId(StandardClassIds.BASE_COLLECTIONS_PACKAGE, Name.identifier("isNotEmpty")), "ifEmpty", negativeCondition = true),
        Replacement(CallableId(StandardClassIds.BASE_TEXT_PACKAGE, Name.identifier("isNotEmpty")), "ifEmpty", negativeCondition = true),
        Replacement(CallableId(StandardClassIds.BASE_TEXT_PACKAGE, Name.identifier("isNotBlank")), "ifBlank", negativeCondition = true),
    ).associateBy { it.conditionFunctionId }

    private val conditionFunctionShortNames: Set<String> = replacements.keys.map { it.callableName.asString() }.toSet()

    internal data class Replacement(
        val conditionFunctionId: CallableId,
        val replacementFunctionName: String,
        val negativeCondition: Boolean = false
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {
        override fun visitIfExpression(expression: KtIfExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(
        element: KtIfExpression,
        context: Replacement
    ): @InspectionMessage String {
        return KotlinBundle.message("replace.with.0", "${context.replacementFunctionName} {...}")
    }

    override fun createQuickFix(
        element: KtIfExpression,
        context: Replacement
    ): KotlinModCommandQuickFix<KtIfExpression> {
        return ReplaceFix(context)
    }

    context(KaSession)
    override fun prepareContext(ifExpression: KtIfExpression): Replacement? {
        if (ifExpression.languageVersionSettings.languageVersion < LanguageVersion.KOTLIN_1_3) return null
        if (ifExpression.node.elementType == KtNodeTypes.ELSE) return null
        val thenExpression = ifExpression.then ?: return null
        val elseExpression = ifExpression.`else` ?: return null
        if (elseExpression is KtIfExpression) return null

        val condition = ifExpression.condition ?: return null
        val conditionCallExpression = condition.getPossiblyQualifiedCallExpression() ?: return null
        val conditionCalleeExpression = conditionCallExpression.calleeExpression ?: return null
        if (conditionCalleeExpression.text !in conditionFunctionShortNames) return null

        val functionSymbol =
            conditionCallExpression.resolveToCall()?.successfulCallOrNull<KaSimpleFunctionCall>()?.partiallyAppliedSymbol ?: return null
        val receiverParameter = functionSymbol.dispatchReceiver ?: functionSymbol.extensionReceiver
        val receiverType = receiverParameter?.type ?: return null
        if (receiverType.isArrayOrPrimitiveArray) return null

        val conditionCallId = functionSymbol.symbol.callableId ?: return null
        val replacement = replacements[conditionCallId] ?: return null

        val selfBranch = if (replacement.negativeCondition) thenExpression else elseExpression
        val selfValueExpression = selfBranch.blockExpressionsOrSingle().singleOrNull() ?: return null

        if (condition is KtDotQualifiedExpression) {
            if (selfValueExpression.text != condition.receiverExpression.text) return null
        } else {
            if (selfValueExpression !is KtThisExpression) return null
        }

        val loop = ifExpression.getStrictParentOfType<KtLoopExpression>()
        if (loop != null) {
            val defaultValueExpression = if (replacement.negativeCondition) elseExpression else thenExpression
            if (defaultValueExpression.anyDescendantOfType<KtExpressionWithLabel> {
                    (it is KtContinueExpression || it is KtBreakExpression) && it.isTargeting(loop)
                }
            ) return null
        }

        return replacement
    }

    private fun KtExpressionWithLabel.isTargeting(loop: KtLoopExpression): Boolean {
        val label = getTargetLabel()

        return if (label == null) parents.firstIsInstanceOrNull<KtLoopExpression>() == loop
            else if (loop.parent !is KtLabeledExpression) false
            else label.mainReference.isReferenceTo(loop) == true
    }

    private class ReplaceFix(@SafeFieldForPreview private val replacement: Replacement) : KotlinModCommandQuickFix<KtIfExpression>() {
        override fun getName() = KotlinBundle.message("replace.with.0", "${replacement.replacementFunctionName} {...}")

        override fun applyFix(
            project: Project,
            element: KtIfExpression,
            updater: ModPsiUpdater
        ) {
            val condition = element.condition ?: return
            val thenExpression = element.then ?: return
            val elseExpression = element.`else` ?: return
            val defaultValueExpression = (if (replacement.negativeCondition) elseExpression else thenExpression)

            val psiFactory = KtPsiFactory(project)
            val receiverText = (condition as? KtDotQualifiedExpression)?.receiverExpression?.text?.let { "$it." } ?: ""
            val replacementFunctionName = replacement.replacementFunctionName
            val newExpression = if (defaultValueExpression is KtBlockExpression) {
                psiFactory.createExpression("${receiverText}$replacementFunctionName ${defaultValueExpression.text}")
            } else {
                psiFactory.createExpressionByPattern("${receiverText}$replacementFunctionName { $0 }", defaultValueExpression)
            }
            element.replace(newExpression)
        }

        override fun getFamilyName() = name
    }
}