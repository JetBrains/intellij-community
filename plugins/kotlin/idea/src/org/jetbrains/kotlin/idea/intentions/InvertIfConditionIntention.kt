// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.InvertIfConditionUtils.copyThenBranchAfter
import org.jetbrains.kotlin.idea.codeinsight.utils.InvertIfConditionUtils.handleStandardCase
import org.jetbrains.kotlin.idea.codeinsight.utils.InvertIfConditionUtils.isEmptyReturn
import org.jetbrains.kotlin.idea.codeinsight.utils.InvertIfConditionUtils.nextEolCommentOnSameLine
import org.jetbrains.kotlin.idea.codeinsight.utils.InvertIfConditionUtils.parentBlockRBrace
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isExitStatement
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.core.unblockDocument
import org.jetbrains.kotlin.idea.inspections.ReplaceNegatedIsEmptyWithIsNotEmptyInspection.Util.invertSelectorFunction
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.psi.patternMatching.matches
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.PsiChildRange
import org.jetbrains.kotlin.psi.psiUtil.lastBlockStatementOrThis
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.trimWhiteSpaces
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.jetbrains.kotlin.utils.addToStdlib.lastIsInstanceOrNull

class InvertIfConditionIntention : SelfTargetingIntention<KtIfExpression>(
    KtIfExpression::class.java,
    KotlinBundle.messagePointer("invert.if.condition")
) {
    override fun isApplicableTo(element: KtIfExpression, caretOffset: Int): Boolean {
        if (!element.ifKeyword.textRange.containsOffset(caretOffset)) return false
        return element.condition != null && element.then != null
    }

    override fun applyTo(element: KtIfExpression, editor: Editor?) {
        val rBrace = parentBlockRBrace(element)
        val commentSavingRange = if (rBrace != null)
            PsiChildRange(element, rBrace)
        else
            PsiChildRange.singleElement(element)

        val commentSaver = CommentSaver(commentSavingRange)
        if (rBrace != null) element.nextEolCommentOnSameLine()?.delete()

        val condition = element.condition!!
        val newCondition = (condition as? KtQualifiedExpression)?.invertSelectorFunction() ?: condition.negate()

        val newIf = handleSpecialCases(element, newCondition) ?: handleStandardCase(element, newCondition)

        val commentRestoreRange = if (rBrace != null)
            PsiChildRange(newIf, rBrace)
        else
            PsiChildRange(newIf, parentBlockRBrace(newIf) ?: newIf)

        commentSaver.restore(commentRestoreRange)

        val newIfCondition = newIf.condition
        (newIfCondition as? KtPrefixExpression)?.let {
            //use De Morgan's law only for negated condition to not make it more complex
            if (it.operationReference.getReferencedNameElementType() == KtTokens.EXCL) {
                val binaryExpr = (it.baseExpression as? KtParenthesizedExpression)?.expression as? KtBinaryExpression
                if (binaryExpr != null) {
                    ConvertBinaryExpressionWithDemorgansLawIntention.Holder.convertIfPossible(binaryExpr)
                }
            }
        }

        editor?.apply {
            unblockDocument()
            moveCaret(newIf.textOffset)
        }
    }

    private fun handleSpecialCases(ifExpression: KtIfExpression, newCondition: KtExpression): KtIfExpression? {
        val elseBranch = ifExpression.`else`
        if (elseBranch != null) return null

        val psiFactory = KtPsiFactory(ifExpression.project)

        val thenBranch = ifExpression.then!!
        val lastThenStatement = thenBranch.lastBlockStatementOrThis()
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
                        exitStatementExecutedAfter(lastStatementInBlock)
                    if (exitStatementAfterIf != null) {
                        val first = afterIfInBlock.first()
                        val last = afterIfInBlock.last()
                        // build new then branch from statements after if (we will add exit statement if necessary later)
                        //TODO: no block if single?
                        val newThenRange = if (isEmptyReturn(lastThenStatement) && isEmptyReturn(lastStatementInBlock)) {
                            PsiChildRange(first, lastStatementInBlock.prevSibling).trimWhiteSpaces()
                        } else {
                            PsiChildRange(first, last).trimWhiteSpaces()
                        }
                        val newIf = psiFactory.createExpressionByPattern("if ($0) { $1 }", newCondition, newThenRange) as KtIfExpression

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
                            val exitAfterNewIf = exitStatementExecutedAfter(updatedIf)
                            if (exitAfterNewIf == null || !exitAfterNewIf.matches(exitStatementAfterIf)) {
                                val newThen = newIf.then as KtBlockExpression
                                newThen.addBefore(exitStatementAfterIf, newThen.rBrace)
                            }
                        }

                        return updatedIf.replace(newIf) as KtIfExpression
                    }
                }
            }
        }


        val exitStatement = exitStatementExecutedAfter(ifExpression) ?: return null

        val updatedIf = copyThenBranchAfter(ifExpression)
        val newIf = psiFactory.createExpressionByPattern("if ($0) $1", newCondition, exitStatement)
        return updatedIf.replace(newIf) as KtIfExpression
    }

    private fun exitStatementExecutedAfter(expression: KtExpression): KtExpression? {
        val parent = expression.parent
        if (parent is KtBlockExpression) {
            val lastStatement = parent.statements.last()
            return if (expression == lastStatement) {
                exitStatementExecutedAfter(parent)
            } else if (lastStatement.isExitStatement() &&
                expression.siblings(withItself = false).firstIsInstance<KtExpression>() == lastStatement
            ) {
                lastStatement
            } else {
                null
            }
        }

        when (parent) {
            is KtNamedFunction -> {
                if (parent.bodyExpression == expression) {
                    if (!parent.hasBlockBody()) return null
                    val returnType = parent.resolveToDescriptorIfAny()?.returnType
                    if (returnType == null || !returnType.isUnit()) return null
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
                        return exitStatementExecutedAfter(pparent)
                    }
                }
            }
        }
        return null
    }
}
