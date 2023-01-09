// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.isElseIf
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.targetLoop
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.blockExpressionsOrSingle
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection

class ReplaceIsEmptyWithIfEmptyInspection : AbstractKotlinInspection() {
    private data class Replacement(
        val conditionFunctionFqName: FqName,
        val replacementFunctionName: String,
        val negativeCondition: Boolean = false
    )

    companion object {
        private val replacements = listOf(
            Replacement(FqName("kotlin.collections.Collection.isEmpty"), "ifEmpty"),
            Replacement(FqName("kotlin.collections.List.isEmpty"), "ifEmpty"),
            Replacement(FqName("kotlin.collections.Set.isEmpty"), "ifEmpty"),
            Replacement(FqName("kotlin.collections.Map.isEmpty"), "ifEmpty"),
            Replacement(FqName("kotlin.text.isEmpty"), "ifEmpty"),
            Replacement(FqName("kotlin.text.isBlank"), "ifBlank"),
            Replacement(FqName("kotlin.collections.isNotEmpty"), "ifEmpty", negativeCondition = true),
            Replacement(FqName("kotlin.text.isNotEmpty"), "ifEmpty", negativeCondition = true),
            Replacement(FqName("kotlin.text.isNotBlank"), "ifBlank", negativeCondition = true),
        ).associateBy { it.conditionFunctionFqName }

        private val conditionFunctionShortNames = replacements.keys.map { it.shortName().asString() }.toSet()
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = ifExpressionVisitor(fun(ifExpression: KtIfExpression) {
        if (ifExpression.languageVersionSettings.languageVersion < LanguageVersion.KOTLIN_1_3) return
        if (ifExpression.isElseIf()) return
        val thenExpression = ifExpression.then ?: return
        val elseExpression = ifExpression.`else` ?: return
        if (elseExpression is KtIfExpression) return

        val condition = ifExpression.condition ?: return
        val conditionCallExpression = condition.getPossiblyQualifiedCallExpression() ?: return
        val conditionCalleeExpression = conditionCallExpression.calleeExpression ?: return
        if (conditionCalleeExpression.text !in conditionFunctionShortNames) return

        val context = ifExpression.analyze(BodyResolveMode.PARTIAL)
        val resultingDescriptor = conditionCallExpression.getResolvedCall(context)?.resultingDescriptor ?: return
        val receiverParameter = resultingDescriptor.dispatchReceiverParameter ?: resultingDescriptor.extensionReceiverParameter
        val receiverType = receiverParameter?.type ?: return
        if (KotlinBuiltIns.isArrayOrPrimitiveArray(receiverType)) return
        val conditionCallFqName = resultingDescriptor.fqNameOrNull() ?: return
        val replacement = replacements[conditionCallFqName] ?: return

        val selfBranch = if (replacement.negativeCondition) thenExpression else elseExpression
        val selfValueExpression = selfBranch.blockExpressionsOrSingle().singleOrNull() ?: return
        if (condition is KtDotQualifiedExpression) {
            if (selfValueExpression.text != condition.receiverExpression.text) return
        } else {
            if (selfValueExpression !is KtThisExpression) return
        }

        val loop = ifExpression.getStrictParentOfType<KtLoopExpression>()
        if (loop != null) {
            val defaultValueExpression = (if (replacement.negativeCondition) elseExpression else thenExpression)
            if (defaultValueExpression.anyDescendantOfType<KtExpression> {
                    (it is KtContinueExpression || it is KtBreakExpression) && (it as KtExpressionWithLabel).targetLoop(context) == loop
                }
            ) return
        }

        holder.registerProblem(
            ifExpression,
            conditionCalleeExpression.textRangeIn(ifExpression),
            KotlinBundle.message("replace.with.0", "${replacement.replacementFunctionName} {...}"),
            ReplaceFix(replacement)
        )
    })

    private class ReplaceFix(@SafeFieldForPreview private val replacement: Replacement) : LocalQuickFix {
        override fun getName() = KotlinBundle.message("replace.with.0", "${replacement.replacementFunctionName} {...}")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val ifExpression = descriptor.psiElement as? KtIfExpression ?: return
            val condition = ifExpression.condition ?: return
            val thenExpression = ifExpression.then ?: return
            val elseExpression = ifExpression.`else` ?: return
            val defaultValueExpression = (if (replacement.negativeCondition) elseExpression else thenExpression)

            val psiFactory = KtPsiFactory(project)
            val receiverText = (condition as? KtDotQualifiedExpression)?.receiverExpression?.text?.let { "$it." } ?: ""
            val replacementFunctionName = replacement.replacementFunctionName
            val newExpression = if (defaultValueExpression is KtBlockExpression) {
                psiFactory.createExpression("${receiverText}$replacementFunctionName ${defaultValueExpression.text}")
            } else {
                psiFactory.createExpressionByPattern("${receiverText}$replacementFunctionName { $0 }", defaultValueExpression)
            }
            ifExpression.replace(newExpression)
        }
    }
}
