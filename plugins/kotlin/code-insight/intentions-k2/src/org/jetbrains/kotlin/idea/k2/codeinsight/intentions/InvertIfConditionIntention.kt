// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityTarget
import org.jetbrains.kotlin.idea.codeinsight.utils.DemorgansLawUtils
import org.jetbrains.kotlin.idea.codeinsight.utils.DemorgansLawUtils.applyDemorgansLaw
import org.jetbrains.kotlin.idea.codeinsight.utils.DemorgansLawUtils.getOperandsIfAllBoolean
import org.jetbrains.kotlin.idea.codeinsight.utils.DemorgansLawUtils.invertSelectorFunction
import org.jetbrains.kotlin.idea.codeinsight.utils.DemorgansLawUtils.splitBooleanSequence
import org.jetbrains.kotlin.idea.codeinsight.utils.DemorgansLawUtils.topmostBinaryExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.InvertIfConditionUtils.copyThenBranchAfter
import org.jetbrains.kotlin.idea.codeinsight.utils.InvertIfConditionUtils.handleStandardCase
import org.jetbrains.kotlin.idea.codeinsight.utils.InvertIfConditionUtils.isEmptyReturn
import org.jetbrains.kotlin.idea.codeinsight.utils.InvertIfConditionUtils.nextEolCommentOnSameLine
import org.jetbrains.kotlin.idea.codeinsight.utils.InvertIfConditionUtils.parentBlockRBrace
import org.jetbrains.kotlin.idea.codeinsight.utils.negate
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isExitStatement
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.jetbrains.kotlin.utils.addToStdlib.lastIsInstanceOrNull

internal class InvertIfConditionIntention :
    AbstractKotlinModCommandWithContext<KtIfExpression, InvertIfConditionIntention.Context>(KtIfExpression::class) {

    data class Context(
        val newCondition: SmartPsiElementPointer<KtExpression>,
        val demorgansLawContext: DemorgansLawUtils.Context?,
        val isParentFunUnit: Boolean,
        val commentSaver: CommentSaver,
    )

    override fun getFamilyName(): String = KotlinBundle.message("invert.if.condition")
    override fun getActionName(element: KtIfExpression, context: Context): String = familyName

    context(KtAnalysisSession)
    override fun prepareContext(element: KtIfExpression): Context {
        val rBrace = parentBlockRBrace(element)
        val commentSavingRange = if (rBrace != null)
            PsiChildRange(element, rBrace)
        else
            PsiChildRange.singleElement(element)

        val commentSaver = CommentSaver(commentSavingRange)

        val condition = element.condition!!
        val newCondition = (condition as? KtQualifiedExpression)?.invertSelectorFunction() ?: condition.negate()

        val isParentFunUnit = element.getParentOfType<KtNamedFunction>(true)?.let { it.getReturnKtType().isUnit } == true

        val demorgansLawContext = if (condition is KtBinaryExpression && areAllOperandsBoolean(condition)) {
            getBinaryExpression(newCondition)?.let(::splitBooleanSequence)?.let { operands ->
                DemorgansLawUtils.prepareContext(operands)
            }
        } else null

        return Context(newCondition.createSmartPointer(), demorgansLawContext, isParentFunUnit, commentSaver)
    }

    private fun getBinaryExpression(expression: KtExpression): KtBinaryExpression? {
        (expression as? KtPrefixExpression)?.let {
            if (it.operationReference.getReferencedNameElementType() == KtTokens.EXCL) {
                val binaryExpr = (it.baseExpression as? KtParenthesizedExpression)?.expression as? KtBinaryExpression
                return binaryExpr?.topmostBinaryExpression()
            }
        }
        return null
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtIfExpression> = applicabilityTarget { ifExpression: KtIfExpression ->
        ifExpression.ifKeyword
    }

    override fun isApplicableByPsi(element: KtIfExpression): Boolean {
        return element.condition != null && element.then != null
    }

    override fun apply(element: KtIfExpression, context: AnalysisActionContext<Context>, updater: ModPsiUpdater) {
        val rBrace = parentBlockRBrace(element)
        if (rBrace != null) element.nextEolCommentOnSameLine()?.delete()

        val analyzeContext = context.analyzeContext
        val newIf = handleSpecialCases(element, analyzeContext) ?: handleStandardCase(element, analyzeContext.newCondition.element!!)

        val commentRestoreRange = if (rBrace != null)
            PsiChildRange(newIf, rBrace)
        else
            PsiChildRange(newIf, parentBlockRBrace(newIf) ?: newIf)

        context.analyzeContext.commentSaver.restore(commentRestoreRange)

        val binaryExpr = newIf.condition?.let(::getBinaryExpression)
        if (binaryExpr != null) {
            context.analyzeContext.demorgansLawContext?.let { demorgansLawContext ->
                applyDemorgansLaw(binaryExpr, demorgansLawContext)
            }
        }

        updater.moveCaretTo(newIf)
    }

    private fun handleSpecialCases(ifExpression: KtIfExpression, context: Context): KtIfExpression? {
        val elseBranch = ifExpression.`else`
        if (elseBranch != null) return null

        val psiFactory = KtPsiFactory(ifExpression.project)

        val thenBranch = ifExpression.then!!
        val lastThenStatement = thenBranch.lastBlockStatementOrThis()
        val newCondition = context.newCondition.element!!
        if (lastThenStatement.isExitStatement()) {
            val block = ifExpression.parent as? KtBlockExpression
            if (block != null) {
                val rBrace = block.rBrace
                val afterIfInBlock = ifExpression.siblings(withItself = false).takeWhile { it != rBrace }.toList()
                val lastStatementInBlock = afterIfInBlock.lastIsInstanceOrNull<KtExpression>()
                if (lastStatementInBlock != null) {
                    val exitStatementAfterIf = if (lastStatementInBlock.isExitStatement())
                        lastStatementInBlock
                    else
                        exitStatementExecutedAfter(lastStatementInBlock, context)
                    if (exitStatementAfterIf != null) {
                        val first = afterIfInBlock.first()
                        val last = afterIfInBlock.last()
                        // build new then branch from statements after if (we will add exit statement if necessary later)
                        val newThenRange = if (isEmptyReturn(lastThenStatement) && isEmptyReturn(lastStatementInBlock)) {
                            PsiChildRange(first, lastStatementInBlock.prevSibling).trimWhiteSpaces()
                        } else {
                            PsiChildRange(first, last).trimWhiteSpaces()
                        }
                        val newIf =
                            psiFactory.createExpressionByPattern("if ($0) { $1 }", newCondition, newThenRange) as KtIfExpression

                        // remove statements after if as they are moving under if
                        block.deleteChildRange(first, last)

                        if (isEmptyReturn(lastThenStatement)) {
                            if (block.parent is KtDeclarationWithBody && block.parent !is KtFunctionLiteral) {
                                lastThenStatement.delete()
                            }
                        }
                        val updatedIf = copyThenBranchAfter(ifExpression)

                        // check if we need to add exit statement to then branch
                        if (exitStatementAfterIf != lastStatementInBlock) {
                            // don't insert the exit statement, if the new if statement placement has the same exit statement executed after it
                            val exitAfterNewIf = exitStatementExecutedAfter(updatedIf, context)
                            if (exitAfterNewIf == null || !matches(exitAfterNewIf, exitStatementAfterIf)) {
                                val newThen = newIf.then as KtBlockExpression
                                newThen.addBefore(exitStatementAfterIf, newThen.rBrace)
                            }
                        }

                        return updatedIf.replace(newIf) as KtIfExpression
                    }
                }
            }
        }

        val exitStatement = exitStatementExecutedAfter(ifExpression, context) ?: return null

        val updatedIf = copyThenBranchAfter(ifExpression)
        val newIf = psiFactory.createExpressionByPattern("if ($0) $1", newCondition, exitStatement)
        return updatedIf.replace(newIf) as KtIfExpression
    }

    private fun matches(exitExpr1: KtExpression, exitExpr2: KtExpression): Boolean {
        return if (exitExpr1 is KtReturnExpression && exitExpr2 is KtReturnExpression) {
            return exitExpr1 == exitExpr2 || (exitExpr1.returnedExpression == null && exitExpr2.returnedExpression == null)
        } else exitExpr1.javaClass == exitExpr2.javaClass
    }

    private fun exitStatementExecutedAfter(expression: KtExpression, context: Context): KtExpression? {
        when (val parent = expression.parent) {
            is KtBlockExpression -> {
                val lastStatement = parent.statements.last()
                return if (expression == lastStatement) {
                    exitStatementExecutedAfter(parent, context)
                } else if (lastStatement.isExitStatement() &&
                    expression.siblings(withItself = false).firstIsInstance<KtExpression>() == lastStatement
                ) {
                    lastStatement
                } else {
                    null
                }
            }
            is KtNamedFunction -> {
                if (parent.bodyExpression == expression && parent.hasBlockBody() && context.isParentFunUnit) {
                    return KtPsiFactory(expression.project).createExpression("return")
                }
            }

            is KtContainerNode -> when (val pparent = parent.parent) {
                is KtLoopExpression -> {
                    if (expression == pparent.body) {
                        return KtPsiFactory(expression.project).createExpression("continue")
                    }
                }

                is KtIfExpression -> {
                    if (expression == pparent.then || expression == pparent.`else`) {
                        return exitStatementExecutedAfter(pparent, context)
                    }
                }
            }
        }
        return null
    }

    context(KtAnalysisSession)
    private fun areAllOperandsBoolean(expression: KtBinaryExpression): Boolean {
        return getOperandsIfAllBoolean(expression) != null
    }
}
