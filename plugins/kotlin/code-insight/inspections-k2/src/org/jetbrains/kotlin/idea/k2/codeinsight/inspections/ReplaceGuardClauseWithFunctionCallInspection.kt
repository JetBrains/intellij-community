// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.ConstantConditionIfUtils.replaceWithBranch
import org.jetbrains.kotlin.idea.codeinsight.utils.NegatedBinaryExpressionSimplificationUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ReplaceGuardClauseWithFunctionCallInspection.KotlinFunction
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.startOffset

private const val ILLEGAL_STATE_EXCEPTION = "IllegalStateException"
private const val ILLEGAL_ARGUMENT_EXCEPTION = "IllegalArgumentException"

internal class ReplaceGuardClauseWithFunctionCallInspection :
    KotlinApplicableInspectionBase.Simple<KtIfExpression, ReplaceGuardClauseWithFunctionCallInspection.Context>() {

    data class Context(
        val kotlinFunction: KotlinFunction,
    )

    enum class KotlinFunction(val functionName: String) {
        CHECK("check"),
        CHECK_NOT_NULL("checkNotNull"),
        REQUIRE("require"),
        REQUIRE_NOT_NULL("requireNotNull");

        val fqName: String
            get() = "kotlin.$functionName"
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = ifExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(
        element: KtIfExpression,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message("replace.guard.clause.with.kotlin.s.function.call")

    override fun isApplicableByPsi(element: KtIfExpression): Boolean {
        if (element.condition == null) return false
        val call = element.getCallExpression() ?: return false
        val calleeText = call.calleeExpression?.text ?: return false
        val valueArguments = call.valueArguments
        if (valueArguments.size > 1) return false
        return calleeText == ILLEGAL_STATE_EXCEPTION || calleeText == ILLEGAL_ARGUMENT_EXCEPTION
    }

    override fun getApplicableRanges(element: KtIfExpression): List<TextRange> =
        ApplicabilityRanges.ifKeyword(element)

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtIfExpression): Context? {
        val call = element.getCallExpression() ?: return null
        val kotlinFunction = element.getKotlinFunction(call) ?: return null
        val calleeText = call.calleeExpression?.text ?: return null
        val valueArguments = call.valueArguments
        val argumentType = valueArguments.firstOrNull()?.getArgumentExpression()?.expressionType
        if (argumentType?.isStringType == false) return null
        if (element.isUsedAsExpression) return null

        val fqName = call.resolveToCall()
            ?.successfulConstructorCallOrNull()
            ?.symbol
            ?.containingClassId
            ?.asSingleFqName() ?: return null

        if (fqName != FqName("kotlin.$calleeText") && fqName != FqName("java.lang.$calleeText")) return null
        return Context(kotlinFunction)
    }

    override fun createQuickFix(
        element: KtIfExpression,
        context: Context
    ): KotlinModCommandQuickFix<KtIfExpression> = object : KotlinModCommandQuickFix<KtIfExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.kotlin.s.function.call")

        override fun getName(): String =
            KotlinBundle.message("replace.with.0.call", context.kotlinFunction.functionName)

        override fun applyFix(
            project: Project,
            element: KtIfExpression,
            updater: ModPsiUpdater,
        ) {
            val condition = element.condition ?: return
            val call = element.getCallExpression() ?: return
            val argument = call.valueArguments.firstOrNull()?.getArgumentExpression()
            val commentSaver = CommentSaver(element)
            val psiFactory = KtPsiFactory(project)
            val replaced = when (val kotlinFunction = element.getKotlinFunction(call)) {
                KotlinFunction.CHECK, KotlinFunction.REQUIRE -> {
                    val (excl, newCondition) = if (condition is KtPrefixExpression && condition.operationToken == KtTokens.EXCL) {
                        "" to (condition.baseExpression ?: return)
                    } else {
                        "!" to condition
                    }
                    val newExpression = if (argument == null) {
                        psiFactory.createExpressionByPattern("${kotlinFunction.fqName}($excl$0)", newCondition)
                    } else {
                        psiFactory.createExpressionByPattern("${kotlinFunction.fqName}($excl$0) { $1 }", newCondition, argument)
                    }
                    val replaced = element.replaceWith(newExpression, psiFactory)
                    val newCall = (replaced as? KtDotQualifiedExpression)?.callExpression
                    val negatedExpression = newCall?.valueArguments?.firstOrNull()?.getArgumentExpression() as? KtPrefixExpression
                    if (negatedExpression != null) {
                        NegatedBinaryExpressionSimplificationUtils.simplifyNegatedBinaryExpressionIfNeeded(negatedExpression)
                    }
                    replaced
                }

                KotlinFunction.CHECK_NOT_NULL, KotlinFunction.REQUIRE_NOT_NULL -> {
                    val nullCheckedExpression = condition.notNullCheckExpression() ?: return
                    val newExpression = if (argument == null) {
                        psiFactory.createExpressionByPattern("${kotlinFunction.fqName}($0)", nullCheckedExpression)
                    } else {
                        psiFactory.createExpressionByPattern("${kotlinFunction.fqName}($0) { $1 }", nullCheckedExpression, argument)
                    }
                    element.replaceWith(newExpression, psiFactory)
                }

                else -> return
            }
            commentSaver.restore(replaced)
            updater.moveCaretTo(replaced.startOffset)
            shortenReferences(replaced)
        }
    }
}

private fun KtIfExpression.replaceWith(newExpression: KtExpression, psiFactory: KtPsiFactory): KtExpression {
    val parent = parent
    val elseBranch = `else`
    return if (elseBranch != null) {
        val added = parent.addBefore(newExpression, this) as KtExpression
        parent.addBefore(psiFactory.createNewLine(), this)
        replaceWithBranch(elseBranch, isUsedAsExpression = false, keepBraces = false)
        added
    } else {
        replaced(newExpression)
    }
}

private fun KtIfExpression.getCallExpression(): KtCallExpression? {
    val throwExpression = this.then?.let {
        it as? KtThrowExpression ?: (it as? KtBlockExpression)?.statements?.singleOrNull() as? KtThrowExpression
    } ?: return null
    return throwExpression.thrownExpression?.let {
        it as? KtCallExpression ?: (it as? KtQualifiedExpression)?.callExpression
    }
}

private fun KtIfExpression.getKotlinFunction(call: KtCallExpression? = getCallExpression()): KotlinFunction? {
    val calleeText = call?.calleeExpression?.text ?: return null
    val isNotNullCheck = condition.notNullCheckExpression() != null
    return when (calleeText) {
        ILLEGAL_STATE_EXCEPTION -> if (isNotNullCheck) KotlinFunction.CHECK_NOT_NULL else KotlinFunction.CHECK
        ILLEGAL_ARGUMENT_EXCEPTION -> if (isNotNullCheck) KotlinFunction.REQUIRE_NOT_NULL else KotlinFunction.REQUIRE
        else -> null
    }
}

private fun KtExpression?.notNullCheckExpression(): KtExpression? {
    if (this == null) return null
    if (this !is KtBinaryExpression) return null
    if (this.operationToken != KtTokens.EQEQ) return null
    val left = this.left ?: return null
    val right = this.right ?: return null
    return when {
        right.isNullConstant() -> left
        left.isNullConstant() -> right
        else -> null
    }
}

private fun KtExpression.isNullConstant(): Boolean =
    (this as? KtConstantExpression)?.text == KtTokens.NULL_KEYWORD.value
