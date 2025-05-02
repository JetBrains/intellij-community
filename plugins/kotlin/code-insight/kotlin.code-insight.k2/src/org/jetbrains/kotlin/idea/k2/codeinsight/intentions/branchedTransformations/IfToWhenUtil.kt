// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations

import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveVisitor
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.AddLoopLabelUtil.getExistingLabelName
import org.jetbrains.kotlin.idea.base.psi.AddLoopLabelUtil.getUniqueLabelName
import org.jetbrains.kotlin.idea.base.psi.getSingleUnwrappedStatementOrThis
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.util.reformat
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

fun convertIfToWhen(element: KtIfExpression, updater: ModPsiUpdater) {
    val ifExpression = element.topmostIfExpression()
    val parent = ifExpression.parent

    val elementCommentSaver = CommentSaver(ifExpression, saveLineBreaks = true)
    val fullCommentSaver = CommentSaver(PsiChildRange(ifExpression, ifExpression.siblings().last()), saveLineBreaks = true)

    val loop = ifExpression.getStrictParentOfType<KtLoopExpression>()
    val loopJumpVisitor = LabelLoopJumpVisitor(loop)

    val toDelete = ArrayList<PsiElement>()

    val (whenExpression, applyFullCommentSaver) = createWhenExpression(ifExpression, toDelete)

    val commentSaver = if (applyFullCommentSaver) fullCommentSaver else elementCommentSaver

    val subjectedWhenExpression = analyze(element) {
        val analysableWhenExpression =
            org.jetbrains.kotlin.psi.KtPsiFactory(element.project).createExpressionCodeFragment(whenExpression.text, ifExpression)
                .getContentElement() as KtWhenExpression

        val subject = analysableWhenExpression.getSubjectToIntroduce(false)
        whenExpression.introduceSubjectIfPossible(subject, ifExpression)
    }

    val result = ifExpression.replaced(subjectedWhenExpression)

    updater.moveCaretTo(result.startOffset)
    commentSaver.restore(result)

    if (toDelete.isNotEmpty()) {
        parent.deleteChildRange(
            toDelete.first().let { it.prevSibling as? PsiWhiteSpace ?: it },
            toDelete.last()
        )
    }

    result.accept(loopJumpVisitor)
    val labelName = loopJumpVisitor.labelName
    if (loop != null && loopJumpVisitor.labelRequired && labelName != null && loop.parent !is KtLabeledExpression) {
        val labeledLoopExpression = org.jetbrains.kotlin.psi.KtPsiFactory(result.project).createLabeledExpression(labelName)
        labeledLoopExpression.baseExpression!!.replace(loop)

        val replacedLabeledLoopExpression = loop.replace(labeledLoopExpression)
        replacedLabeledLoopExpression.reformat()
    }
}

private fun createWhenExpression(
    ifExpression: KtIfExpression,
    toDelete: ArrayList<PsiElement>
): Pair<KtWhenExpression, Boolean> {
    var applyFullCommentSaver = true
    val whenExpression = KtPsiFactory(ifExpression.project).buildExpression {
        appendFixedText("when {\n")

        var currentIfExpression = ifExpression
        var baseIfExpressionForSyntheticBranch = currentIfExpression
        var canPassThrough = false
        while (true) {
            val condition = currentIfExpression.condition
            val orBranches = ArrayList<KtExpression>()
            if (condition != null) {
                orBranches.addOrBranches(condition)
            }

            appendExpressions(orBranches, separator = "||")

            appendFixedText("->")

            val currentThenBranch = currentIfExpression.then
            appendExpression(currentThenBranch)
            appendFixedText("\n")

            canPassThrough = canPassThrough || canPassThrough(currentThenBranch)

            val currentElseBranch = currentIfExpression.`else`
            if (currentElseBranch == null) {
                // Try to build synthetic if / else according to KT-10750
                val syntheticElseBranch = if (canPassThrough) null else buildNextBranch(baseIfExpressionForSyntheticBranch)
                if (syntheticElseBranch == null) {
                    applyFullCommentSaver = false
                    break
                }
                toDelete.addAll(baseIfExpressionForSyntheticBranch.siblingsUpTo(syntheticElseBranch))
                if (syntheticElseBranch is KtIfExpression) {
                    baseIfExpressionForSyntheticBranch = syntheticElseBranch
                    currentIfExpression = syntheticElseBranch
                    toDelete.add(syntheticElseBranch)
                } else {
                    appendElseBlock(syntheticElseBranch, unwrapBlockOrParenthesis = true)
                    break
                }
            } else if (currentElseBranch is KtIfExpression) {
                currentIfExpression = currentElseBranch
            } else {
                appendElseBlock(currentElseBranch)
                applyFullCommentSaver = false
                break
            }
        }

        appendFixedText("}")
    } as KtWhenExpression

    return Pair(whenExpression, applyFullCommentSaver)
}

private fun KtIfExpression.topmostIfExpression(): KtIfExpression {
    var target = this
    while (true) {
        val container = target.parent as? KtContainerNodeForControlStructureBody ?: break
        val parent = container.parent as? KtIfExpression ?: break
        if (parent.`else` != target) break
        target = parent
    }
    return target
}

private fun canPassThrough(expression: KtExpression?): Boolean = when (expression) {
    is KtReturnExpression, is KtThrowExpression, is KtCallExpression, is KtStringTemplateExpression ->
        false
    is KtBlockExpression ->
        expression.statements.all { canPassThrough(it) }
    is KtIfExpression ->
        canPassThrough(expression.then) || canPassThrough(expression.`else`)
    else ->
        true
}

private fun buildNextBranch(ifExpression: KtIfExpression): KtExpression? {
    var nextSibling = ifExpression.getNextSiblingIgnoringWhitespaceAndComments() ?: return null
    return when (nextSibling) {
        is KtIfExpression ->
            if (nextSibling.then == null) null else nextSibling

        else -> {
            val builder = StringBuilder()
            while (true) {
                builder.append(nextSibling.text)
                nextSibling = nextSibling.nextSibling ?: break
            }
            KtPsiFactory(ifExpression.project).createBlock(builder.toString()).takeIf { it.statements.isNotEmpty() }
        }
    }
}

private fun MutableList<KtExpression>.addOrBranches(expression: KtExpression): List<KtExpression> {
    if (expression is KtBinaryExpression && expression.operationToken == KtTokens.OROR) {
        val left = expression.left
        val right = expression.right
        if (left != null && right != null) {
            addOrBranches(left)
            addOrBranches(right)
            return this
        }
    }

    add(KtPsiUtil.safeDeparenthesize(expression, true))
    return this
}

private fun BuilderByPattern<*>.appendElseBlock(block: KtExpression?, unwrapBlockOrParenthesis: Boolean = false) {
    appendFixedText("else->")
    appendExpression(if (unwrapBlockOrParenthesis) block?.getSingleUnwrappedStatementOrThis() else block)
    appendFixedText("\n")
}

private fun KtIfExpression.siblingsUpTo(other: KtExpression): List<PsiElement> {
    val result = ArrayList<PsiElement>()
    var nextSibling = nextSibling
    // We delete elements up to the next if (or up to the end of the surrounding block)
    while (nextSibling != null && nextSibling != other) {
        // RBRACE closes the surrounding block, so it should not be copied / deleted
        if (nextSibling !is PsiWhiteSpace && nextSibling.node.elementType != KtTokens.RBRACE) {
            result.add(nextSibling)
        }
        nextSibling = nextSibling.nextSibling
    }

    return result
}


private class LabelLoopJumpVisitor(private val nearestLoopIfAny: KtLoopExpression?) : KtVisitorVoid(),PsiRecursiveVisitor {
    val labelName: String? by lazy {
        nearestLoopIfAny?.let { loop ->
            getExistingLabelName(loop) ?: getUniqueLabelName(loop)
        }
    }

    var labelRequired = false

    fun KtExpressionWithLabel.addLabelIfNecessary(): KtExpressionWithLabel {
        if (this.getLabelName() != null) {
            // Label is already present, no need to add
            return this
        }

        if (this.getStrictParentOfType<KtLoopExpression>() != nearestLoopIfAny) {
            // 'for' inside 'if'
            return this
        }

        if (!languageVersionSettings.supportsFeature(LanguageFeature.AllowBreakAndContinueInsideWhen) && labelName != null) {
            val jumpWithLabel = KtPsiFactory(project).createExpression("$text@$labelName") as KtExpressionWithLabel
            labelRequired = true
            return replaced(jumpWithLabel)
        }

        return this
    }

    override fun visitBreakExpression(expression: KtBreakExpression) {
        expression.addLabelIfNecessary()
    }

    override fun visitContinueExpression(expression: KtContinueExpression) {
        expression.addLabelIfNecessary()
    }

    override fun visitKtElement(element: KtElement) {
        element.acceptChildren(this)
    }
}