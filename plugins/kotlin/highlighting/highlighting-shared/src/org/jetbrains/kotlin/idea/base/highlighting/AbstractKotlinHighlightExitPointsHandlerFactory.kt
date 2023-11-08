// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfTypes
import com.intellij.util.Consumer
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.*

abstract class AbstractKotlinHighlightExitPointsHandlerFactory : HighlightUsagesHandlerFactoryBase() {
    private fun getOnReturnOrThrowUsageHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        val expression = when (val parent = target.parent) {
            is KtNamedFunction -> parent.takeIf { (target as? ASTNode)?.elementType == KtTokens.FUN_KEYWORD }
            is KtPropertyAccessor -> parent
            is KtReturnExpression, is KtThrowExpression -> parent
            is KtLabelReferenceExpression ->
                PsiTreeUtil.getParentOfType(
                    target, KtReturnExpression::class.java, KtThrowExpression::class.java, KtFunction::class.java
                )?.takeUnless { it is KtFunction }
            else -> null
        } as? KtExpression ?: return null
        return OnExitUsagesHandler(editor, file, expression, false)
    }

    private fun getOnBreakOrContinueUsageHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        val expression = when (val parent = target.parent) {
            is KtBreakExpression, is KtContinueExpression -> parent
            is KtDoWhileExpression -> parent.takeIf { target.elementType == KtTokens.DO_KEYWORD }
            is KtLoopExpression -> parent
            else -> null
        } as? KtExpression ?: return null
        return OnLoopUsagesHandler(editor, file, expression)
    }

    private fun getOnLambdaCallUsageHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        if (target !is LeafPsiElement
            || target.elementType !is KtToken // do not trigger loading of KtTokens in Java
            || target.elementType != KtTokens.IDENTIFIER) {
            return null
        }

        val literal = (target.parent as? KtNameReferenceExpression).asFunctionLiteral() ?: return null
        return OnExitUsagesHandler(editor, file, literal, true)
    }

    private fun KtNameReferenceExpression?.asFunctionLiteral(): KtFunctionLiteral? {
        val call = this?.parent as? KtCallExpression ?: return null
        if (call.calleeExpression != this) return null

        val lambda = call.lambdaArguments.singleOrNull() ?: return null
        val literal = lambda.getLambdaExpression()?.functionLiteral ?: return null
        return literal
    }

    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        return getOnReturnOrThrowUsageHandler(editor, file, target)
            ?: getOnBreakOrContinueUsageHandler(editor, file, target)
            ?: getOnLambdaCallUsageHandler(editor, file, target)
    }

    protected abstract fun getRelevantReturnDeclaration(returnExpression: KtReturnExpression): KtDeclarationWithBody?

    protected abstract fun isInlinedArgument(declaration: KtDeclarationWithBody): Boolean

    protected abstract fun hasNonUnitReturnType(functionLiteral: KtFunctionLiteral): Boolean

    protected fun getRelevantDeclaration(expression: KtExpression): KtDeclarationWithBody? {
        if (expression is KtReturnExpression) {
            getRelevantReturnDeclaration(expression)?.let { return it }
        }

        if (expression is KtThrowExpression || expression is KtReturnExpression) {
            for (parent in expression.parents) {
                if (parent is KtDeclarationWithBody) {
                    if (parent is KtPropertyAccessor) {
                        return parent
                    }

                    if ((parent is KtFunctionLiteral || parent is KtNamedFunction) && !isInlinedArgument(parent)) {
                        return parent
                    }
                }
            }

            return null
        }

        return expression.parents.filterIsInstance<KtDeclarationWithBody>().firstOrNull()
    }

    private inner class OnExitUsagesHandler(editor: Editor, file: PsiFile, val target: KtExpression, val highlightReferences: Boolean) :
        HighlightUsagesHandlerBase<PsiElement>(editor, file) {

        override fun getTargets(): List<KtExpression> = listOf(target)

        override fun selectTargets(targets: List<PsiElement>, selectionConsumer: Consumer<in List<PsiElement>>) {
            selectionConsumer.consume(targets)
        }

        override fun computeUsages(targets: List<PsiElement>) {
            val relevantFunction: KtDeclarationWithBody? =
                when (target) {
                    is KtFunctionLiteral -> target
                    is KtPropertyAccessor -> target
                    is KtNamedFunction -> target
                    else -> getRelevantDeclaration(target)
                }

            var targetOccurrenceAdded = false
            if (target is KtReturnExpression || target is KtThrowExpression || target is KtNamedFunction || target is KtPropertyAccessor) {
                when (relevantFunction) {
                    is KtNamedFunction -> relevantFunction.funKeyword?.let {
                        targetOccurrenceAdded = true
                        addOccurrence(it)
                    }
                    is KtPropertyAccessor -> relevantFunction.namePlaceholder.let {
                        targetOccurrenceAdded = true
                        addOccurrence(it)
                    }
                    is KtFunctionLiteral -> relevantFunction.getStrictParentOfType<KtLambdaArgument>()
                        ?.getStrictParentOfType<KtCallExpression>()
                        ?.calleeExpression
                        ?.let {
                            targetOccurrenceAdded = true
                            addOccurrence(it)
                        }
                }
            }

            val lastStatementExpressions =
                if ((relevantFunction is KtFunctionLiteral && hasNonUnitReturnType(relevantFunction)) ||
                    (relevantFunction is KtNamedFunction && relevantFunction.bodyBlockExpression == null)
                ) {
                    val lastStatements = mutableSetOf<PsiElement>(relevantFunction)
                    relevantFunction.acceptChildren(object : KtVisitorVoid() {
                        override fun visitKtElement(element: KtElement) {
                            ProgressIndicatorProvider.checkCanceled()
                            element.acceptChildren(this)
                        }

                        override fun visitExpression(expression: KtExpression) {
                            when(expression) {
                                is KtBinaryExpression -> {
                                    expression.left?.let {
                                        lastStatements.addIfNotNullAndNotBlock(it)
                                        visitExpression(it)
                                    }
                                    expression.right?.let {
                                        lastStatements.addIfNotNullAndNotBlock(it)
                                        visitExpression(it)
                                    }
                                }
                                is KtCallExpression -> {
                                    expression.calleeExpression?.let {
                                        lastStatements.addIfNotNullAndNotBlock(it)
                                        visitExpression(it)
                                    }
                                }
                                is KtBlockExpression -> {
                                    expression.lastStatementOrNull()?.let {
                                        lastStatements.addIfNotNullAndNotBlock(it)
                                        visitExpression(it)
                                    }
                                }
                                is KtIfExpression -> {
                                    expression.then?.let {
                                        lastStatements.addIfNotNullAndNotBlock(it)
                                        visitExpression(it)
                                    }
                                    expression.`else`?.let{
                                        lastStatements.addIfNotNullAndNotBlock(it)
                                        visitExpression(it)
                                    }
                                }
                                is KtWhenExpression -> {
                                    expression.entries.mapNotNull { it.expression }.forEach {
                                        lastStatements.addIfNotNullAndNotBlock(it)
                                        visitExpression(it)
                                    }
                                }
                                else -> super.visitExpression(expression)
                            }
                        }
                    })
                    if (target !is KtReturnExpression && target !is KtThrowExpression && target !in lastStatements) {
                        return
                    }
                    lastStatements
                } else {
                    emptySet()
                }

            relevantFunction?.accept(object : KtVisitorVoid() {
                override fun visitKtElement(element: KtElement) {
                    ProgressIndicatorProvider.checkCanceled()
                    element.acceptChildren(this)
                }

                override fun visitExpression(expression: KtExpression) {
                    if (relevantFunction is KtFunctionLiteral || relevantFunction is KtNamedFunction) {
                        if (occurrenceForFunctionLiteralReturnExpression(expression, expression in lastStatementExpressions)) {
                            addTargetOccurenceIfNeeded(relevantFunction)
                            return
                        }
                    }

                    super.visitExpression(expression)
                }

                private fun addTargetOccurenceIfNeeded(relevantFunction: KtDeclarationWithBody) {
                    if (!targetOccurrenceAdded) {
                        when (relevantFunction) {
                            is KtFunctionLiteral -> relevantFunction.parentOfTypes(KtCallExpression::class)?.calleeExpression
                            is KtNamedFunction -> relevantFunction.funKeyword
                            else -> null
                        }?.let {
                            targetOccurrenceAdded = true
                            addOccurrence(it)
                        }
                    }
                }

                private fun occurrenceForFunctionLiteralReturnExpression(expression: KtExpression, lastLambdaExpression: Boolean): Boolean {
                    if (!KtPsiUtil.isStatement(expression)) return false

                    if (expression is KtIfExpression || expression is KtWhenExpression || expression is KtBlockExpression) {
                        return false
                    }

                    if (!lastLambdaExpression) {
                        return false
                    }

                    if (getRelevantDeclaration(expression) != relevantFunction) {
                        return false
                    }

                    addOccurrence(expression)
                    return true
                }

                private fun isRelevantFunction(expression: KtExpression): Boolean = getRelevantDeclaration(expression) == relevantFunction

                override fun visitReturnExpression(expression: KtReturnExpression) {
                    if (!isRelevantFunction(expression)) return

                    when (expression.returnedExpression) {
                        is KtIfExpression, is KtWhenExpression, is KtTryExpression -> {
                            addOccurrence(expression.returnKeyword)
                            expression.acceptChildren(object : KtVisitorVoid() {
                                override fun visitKtElement(element: KtElement) {
                                    ProgressIndicatorProvider.checkCanceled()
                                    element.acceptChildren(this)
                                }

                                override fun visitExpression(expression: KtExpression) {
                                    when (expression) {
                                        is KtBlockExpression -> expression.lastStatementOrNull()?.let { visitExpression(it) }
                                        is KtIfExpression -> {
                                            expression.then?.let { visitExpression(it) }
                                            expression.`else`?.let { visitExpression(it) }
                                        }

                                        is KtTryExpression -> {
                                            expression.tryBlock.lastStatementOrNull()?.let { visitExpression(it) }
                                            expression.catchClauses.forEach { catchClause ->
                                                catchClause.catchBody?.let { visitExpression(it) }
                                            }
                                        }

                                        is KtWhenExpression ->
                                            expression.entries.forEach { whenEntry ->
                                                whenEntry.expression?.let { visitExpression(it) }
                                            }

                                        else -> addOccurrence(expression)
                                    }
                                }
                            })
                        }

                        else -> addOccurrence(expression)
                    }

                    addTargetOccurenceIfNeeded(relevantFunction)
                }

                override fun visitThrowExpression(expression: KtThrowExpression) {
                    if (!isRelevantFunction(expression)) return

                    addOccurrence(expression)
                }
            })
        }

        override fun highlightReferences(): Boolean = highlightReferences
    }

    private inner class OnLoopUsagesHandler(editor: Editor, file: PsiFile, val target: KtExpression) :
        HighlightUsagesHandlerBase<PsiElement>(editor, file) {
        override fun getTargets(): List<KtExpression> = listOf(target)

        override fun selectTargets(targets: List<PsiElement>, selectionConsumer: Consumer<in List<PsiElement>>) {
            selectionConsumer.consume(targets)
        }

        override fun computeUsages(targets: MutableList<out PsiElement>) {
            val labelName = when (target) {
                is KtExpressionWithLabel -> target.getLabelName()
                is KtLoopExpression -> (target.parent as? KtLabeledExpression)?.getLabelName()
                else -> null
            }
            val relevantLoop: KtLoopExpression = when (target) {
                is KtLoopExpression -> target
                else -> {
                    var element: PsiElement? = target
                    var targetLoop: KtLoopExpression? = null
                    while (element != null) {
                        val parent = element.parent
                        if (element is KtLoopExpression && (labelName == null || (parent as? KtLabeledExpression)?.getLabelName() == labelName)) {
                            targetLoop = element
                            break
                        }
                        element = parent
                    }
                    targetLoop
                }
            } ?: return

            when(relevantLoop) {
                is KtForExpression -> addOccurrence(relevantLoop.forKeyword)
                is KtDoWhileExpression -> relevantLoop.node.findChildByType(KtTokens.DO_KEYWORD)?.psi?.let(::addOccurrence)
                is KtWhileExpression -> relevantLoop.node.findChildByType(KtTokens.WHILE_KEYWORD)?.psi?.let(::addOccurrence)
            }



            relevantLoop.accept(object : KtVisitorVoid() {
                var nestedLoopExpressions = Stack<KtLoopExpression>()

                override fun visitKtElement(element: KtElement) {
                    ProgressIndicatorProvider.checkCanceled()
                    element.acceptChildren(this)
                }

                override fun visitExpression(expression: KtExpression) {
                    val nestedLoopFound = if (expression != relevantLoop && expression is KtLoopExpression) {
                        val loopLabelName = (expression.parent as? KtLabeledExpression)?.getLabelName()
                        // no reasons to step into another loop with the same label name or no label name
                        if (labelName == null || labelName == loopLabelName) return

                        nestedLoopExpressions.push(expression)
                        true
                    } else {
                        false
                    }

                    if (expression is KtBreakExpression || expression is KtContinueExpression) {
                        val expressionLabelName = (expression as? KtExpressionWithLabel)?.getLabelName()
                        if (nestedLoopExpressions.isEmpty()) {
                            if (expressionLabelName == null || expressionLabelName == labelName) {
                                addOccurrence(expression)
                            }
                        } else if (expressionLabelName == labelName) {
                            addOccurrence(expression)
                        }
                    }

                    try {
                        super.visitExpression(expression)
                    } finally {
                        if (nestedLoopFound) {
                            nestedLoopExpressions.pop()
                        }
                    }
                }
            })
        }

    }

    private fun MutableSet<PsiElement>.addIfNotNullAndNotBlock(element: PsiElement?) {
        addIfNotNull(element.takeUnless { it is KtBlockExpression })
    }

    private fun KtBlockExpression.lastStatementOrNull(): KtExpression? {
        var expression: KtExpression? = null
        var cur = getFirstChild()
        while (cur != null) {
            (cur as? KtExpression)?.let { expression = it }
            cur = cur.getNextSibling()
        }
        return expression
    }

}