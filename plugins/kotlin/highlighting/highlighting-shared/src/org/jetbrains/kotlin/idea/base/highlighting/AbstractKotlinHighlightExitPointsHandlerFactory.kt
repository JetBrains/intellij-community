// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.find.FindManager
import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.impl.FindManagerImpl
import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.util.Consumer
import org.jetbrains.kotlin.idea.references.unwrappedTargets
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.*

abstract class AbstractKotlinHighlightExitPointsHandlerFactory : HighlightUsagesHandlerFactoryBase() {
    private fun getOnReturnOrThrowOrLambdaUsageHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        val expression = when (val parent = target.parent) {
            is KtNamedFunction -> parent.takeIf { (target as? ASTNode)?.elementType == KtTokens.FUN_KEYWORD }
            is KtPropertyAccessor -> parent
            is KtReturnExpression, is KtThrowExpression -> parent
            is KtFunctionLiteral -> parent.takeIf { with((target as? ASTNode)?.elementType) { this == KtTokens.LBRACE || this == KtTokens.RBRACE } }
            is KtLabelReferenceExpression ->
                PsiTreeUtil.getParentOfType(
                    target, KtReturnExpression::class.java, KtThrowExpression::class.java, KtFunction::class.java
                )?.takeUnless {
                    it is KtFunction
                }
            else -> null
        } as? KtExpression ?: return null
        return OnExitUsagesHandler(editor, file, null, expression, false)
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

    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        return getOnReturnOrThrowOrLambdaUsageHandler(editor, file, target)
            ?: getOnBreakOrContinueUsageHandler(editor, file, target)
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

    private inner class OnExitUsagesHandler(
        editor: Editor,
        file: PsiFile,
        val referenceExpression: KtNameReferenceExpression?,
        val target: KtExpression,
        val highlightReferences: Boolean
    ) :
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
                    is KtFunctionLiteral -> {
                        targetOccurrenceAdded = true
                        addOccurrence(relevantFunction.lBrace)
                        relevantFunction.rBrace?.let(::addOccurrence)
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

            if (relevantFunction != null) {
                val containingFile = relevantFunction.containingFile
                referenceExpression?.reference?.unwrappedTargets?.firstOrNull()?.let { target ->
                    (target as? PsiNameIdentifierOwner)?.nameIdentifier.takeIf { target.containingFile == containingFile }
                        ?.let(::addOccurrence)

                    val handler: FindUsagesHandler? = (FindManager.getInstance(relevantFunction.project) as FindManagerImpl)
                        .findUsagesManager
                        .getFindUsagesHandler(target, true)
                    handler?.findReferencesToHighlight(target, LocalSearchScope(containingFile)).let { ref ->
                        ref?.forEach { addOccurrence(it.element) }
                    }
                }
            }
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