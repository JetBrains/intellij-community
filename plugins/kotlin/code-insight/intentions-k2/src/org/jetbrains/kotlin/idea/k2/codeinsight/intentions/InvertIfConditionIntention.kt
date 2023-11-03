// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityTarget
import org.jetbrains.kotlin.idea.codeinsight.utils.*
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.jetbrains.kotlin.utils.addToStdlib.lastIsInstanceOrNull

internal class InvertIfConditionIntention : AbstractKotlinModCommandWithContext<KtIfExpression, Context>(KtIfExpression::class) {
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
        val areAllOperandsBoolean =
            (condition is KtBinaryExpression && splitBooleanSequence(condition)?.all { it.isBoolean } == true) || condition.isBoolean
        val newCondition = (condition as? KtQualifiedExpression)?.invertSelectorFunction() ?: condition.negate()

        val isParentFunUnit = element.getParentOfType<KtNamedFunction>(true)
        val isUnit = isParentFunUnit != null && isParentFunUnit.getReturnKtType().isUnit

        val demorgansLawContext = if (areAllOperandsBoolean) {
            getBinaryExpression(newCondition)?.let(::splitBooleanSequence)?.let { expressions ->
                prepareDemorgansLawContext(expressions)
            }
        } else null

        return Context(newCondition.createSmartPointer(), demorgansLawContext, isUnit, commentSaver)
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
        val newIf = handleSpecialCases(element, analyzeContext) ?: handleStandardCase(element, analyzeContext)

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

        updater.moveTo(newIf)
    }

    private fun handleStandardCase(ifExpression: KtIfExpression, context: Context): KtIfExpression {
        val psiFactory = KtPsiFactory(ifExpression.project)

        val thenBranch = ifExpression.then!!
        val elseBranch = ifExpression.`else` ?: psiFactory.createEmptyBody()

        val newThen = if (elseBranch is KtIfExpression)
            psiFactory.createSingleStatementBlock(elseBranch)
        else
            elseBranch

        val newElse = if (thenBranch is KtBlockExpression && thenBranch.statements.isEmpty())
            null
        else
            thenBranch

        val conditionLineNumber = ifExpression.condition?.getLineNumber(false)
        val thenBranchLineNumber = thenBranch.getLineNumber(false)
        val elseKeywordLineNumber = ifExpression.elseKeyword?.getLineNumber()
        val afterCondition = if (newThen !is KtBlockExpression && elseKeywordLineNumber != elseBranch.getLineNumber(false)) "\n" else ""
        val beforeElse = if (newThen !is KtBlockExpression && conditionLineNumber != elseKeywordLineNumber) "\n" else " "
        val afterElse = if (newElse !is KtBlockExpression && conditionLineNumber != thenBranchLineNumber) "\n" else " "

        val newCondition = context.newCondition.element!!
        val newIf = if (newElse == null) {
            psiFactory.createExpressionByPattern("if ($0)$afterCondition$1", newCondition, newThen)
        } else {
            psiFactory.createExpressionByPattern("if ($0)$afterCondition$1${beforeElse}else$afterElse$2", newCondition, newThen, newElse)
        } as KtIfExpression

        return ifExpression.replaced(newIf)
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

    private fun isEmptyReturn(statement: KtExpression) =
        statement is KtReturnExpression && statement.returnedExpression == null && statement.labeledExpression == null

    private fun copyThenBranchAfter(ifExpression: KtIfExpression): KtIfExpression {
        val psiFactory = KtPsiFactory(ifExpression.project)
        val thenBranch = ifExpression.then ?: return ifExpression

        val parent = ifExpression.parent
        if (parent !is KtBlockExpression) {
            assert(parent is KtContainerNode)
            val block = psiFactory.createEmptyBody()
            block.addAfter(ifExpression, block.lBrace)
            val newBlock = ifExpression.replaced(block)
            val newIf = newBlock.statements.single() as KtIfExpression
            return copyThenBranchAfter(newIf)
        }

        if (thenBranch is KtBlockExpression) {
            (thenBranch.statements.lastOrNull() as? KtContinueExpression)?.delete()
            val range = thenBranch.contentRange()
            if (!range.isEmpty) {
                parent.addRangeAfter(range.first, range.last, ifExpression)
                parent.addAfter(psiFactory.createNewLine(), ifExpression)
            }
        } else if (thenBranch !is KtContinueExpression) {
            parent.addAfter(thenBranch, ifExpression)
            parent.addAfter(psiFactory.createNewLine(), ifExpression)
        }
        return ifExpression
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

    private fun KtExpression.isExitStatement(): Boolean = when (this) {
        is KtContinueExpression, is KtBreakExpression, is KtThrowExpression, is KtReturnExpression -> true
        else -> false
    }

    private fun parentBlockRBrace(element: KtIfExpression): PsiElement? = (element.parent as? KtBlockExpression)?.rBrace

    private fun KtIfExpression.nextEolCommentOnSameLine(): PsiElement? = getLineNumber(false).let { lastLineNumber ->
        siblings(withItself = false)
            .takeWhile { it.getLineNumber() == lastLineNumber }
            .firstOrNull { it is PsiComment && it.node.elementType == KtTokens.EOL_COMMENT }
    }
}

internal class Context(
    val newCondition: SmartPsiElementPointer<KtExpression>,
    val demorgansLawContext: DemorgansLawContext?,
    val isParentFunUnit: Boolean,
    val commentSaver: CommentSaver,
)
