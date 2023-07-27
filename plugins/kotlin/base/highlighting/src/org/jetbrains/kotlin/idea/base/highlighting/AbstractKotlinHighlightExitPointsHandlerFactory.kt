// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.highlighting

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfTypes
import com.intellij.util.Consumer
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.utils.addIfNotNull

abstract class AbstractKotlinHighlightExitPointsHandlerFactory : HighlightUsagesHandlerFactoryBase() {
    private fun getOnReturnOrThrowUsageHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        val expression = when (val parent = target.parent) {
            is KtNamedFunction -> parent
            is KtReturnExpression, is KtThrowExpression -> parent
            is KtLabelReferenceExpression ->
                PsiTreeUtil.getParentOfType(
                    target, KtReturnExpression::class.java, KtThrowExpression::class.java, KtFunction::class.java
                )?.takeUnless { it is KtFunction }
            is KtIfExpression, is KtWhenExpression, is KtBlockExpression -> null
            is KtExpression -> {
                var ktFunction: KtFunction? = (parent as? KtNameReferenceExpression)?.asFunctionLiteral()
                val expressions = hashSetOf<KtExpression>()
                if (ktFunction == null) {
                    var node: PsiElement? = parent
                    while (node != null) {
                        val nextParent = node.parent
                        when (node) {
                            is PsiFile, is KtClassOrObject -> break
                            //is PsiFile, is KtNamedFunction, is KtClassOrObject -> break
                            is KtNamedFunction -> {
                                ktFunction = node
                                break
                            }

                            is KtFunctionLiteral -> {
                                ktFunction = node
                                break
                            }

                            is KtExpression -> expressions += node
                            else -> {
                                if ((nextParent is KtIfExpression || nextParent is KtWhenExpression || nextParent is KtBlockExpression) &&
                                    !KtPsiUtil.isStatementContainer(node)
                                ) {
                                    break
                                }
                            }
                        }
                        node = nextParent
                    }
                }
                ktFunction?.let {
                    val lastStatement =
                        when(it) {
                            is KtFunctionLiteral -> it.bodyExpression?.lastStatementOrNull()
                            is KtNamedFunction -> {
                                val bodyBlockExpression = it.bodyBlockExpression
                                if (bodyBlockExpression != null) {
                                    bodyBlockExpression.lastStatementOrNull()
                                } else {
                                    it.bodyExpression
                                }
                            }
                            else -> null
                        }
                    if (lastStatement in expressions) {
                        if (ktFunction is KtFunctionLiteral) {
                            parent
                        } else if (ktFunction.bodyBlockExpression != null) {
                            parent.takeIf { _ ->
                                expressions.any { e -> e is KtReturnExpression || e is KtThrowExpression }
                            }
                        } else {
                            parent
                        }
                    } else {
                        null
                    }
                }
            }
            else -> null
        } as? KtExpression ?: return null
        return OnExitUsagesHandler(editor, file, expression)
    }

    private fun getOnLambdaCallUsageHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        if (target !is LeafPsiElement
            || target.elementType !is KtToken // do not trigger loading of KtTokens in Java
            || target.elementType != KtTokens.IDENTIFIER) {
            return null
        }

        val literal = (target.parent as? KtNameReferenceExpression).asFunctionLiteral() ?: return null
        return OnExitUsagesHandler(editor, file, literal, highlightReferences = true)
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
            ?: getOnLambdaCallUsageHandler(editor, file, target)
    }

    protected abstract fun getRelevantReturnDeclaration(returnExpression: KtReturnExpression): KtDeclarationWithBody?

    protected abstract fun isInlinedArgument(declaration: KtDeclarationWithBody): Boolean

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

                    if (InlineUtil.canBeInlineArgument(parent) && !isInlinedArgument(parent)) {
                        return parent
                    }
                }
            }

            return null
        }

        return expression.parents.filterIsInstance<KtDeclarationWithBody>().firstOrNull()
    }

    private inner class OnExitUsagesHandler(editor: Editor, file: PsiFile, val target: KtExpression, val highlightReferences: Boolean = false) :
        HighlightUsagesHandlerBase<PsiElement>(editor, file) {

        override fun getTargets() = listOf(target)

        override fun selectTargets(targets: List<PsiElement>, selectionConsumer: Consumer<in List<PsiElement>>) {
            selectionConsumer.consume(targets)
        }

        override fun computeUsages(targets: List<PsiElement>) {
            val relevantFunction: KtDeclarationWithBody? =
                when (target) {
                    is KtFunctionLiteral -> target
                    is KtNamedFunction -> target
                    else -> getRelevantDeclaration(target)
                }

            var targetOccurrenceAdded = false
            if (target is KtReturnExpression || target is KtThrowExpression || target is KtNamedFunction) {
                when (relevantFunction) {
                    is KtNamedFunction -> (relevantFunction.nameIdentifier ?: relevantFunction.funKeyword)?.let {
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
                if (relevantFunction is KtFunctionLiteral ||
                    (relevantFunction is KtNamedFunction && relevantFunction.bodyBlockExpression == null)
                ) {
                    val lastStatementStatements = mutableSetOf<PsiElement>(relevantFunction)
                    relevantFunction.acceptChildren(object : KtVisitorVoid() {
                        override fun visitKtElement(element: KtElement) {
                            ProgressIndicatorProvider.checkCanceled()
                            element.acceptChildren(this)
                        }

                        override fun visitExpression(expression: KtExpression) {
                            when(expression) {
                                is KtBinaryExpression -> {
                                    lastStatementStatements.addIfNotNullAndNotBlock(expression.left)
                                    lastStatementStatements.addIfNotNullAndNotBlock(expression.right)
                                }
                                is KtCallExpression -> {
                                    lastStatementStatements.addIfNotNullAndNotBlock(expression.calleeExpression)
                                }
                                is KtBlockExpression -> lastStatementStatements.addIfNotNullAndNotBlock(expression.lastStatementOrNull())
                                is KtIfExpression -> {
                                    lastStatementStatements.addIfNotNullAndNotBlock(expression.then)
                                    lastStatementStatements.addIfNotNullAndNotBlock(expression.`else`)
                                }
                                is KtWhenExpression ->
                                    expression.entries.map { it.expression }.forEach {
                                        lastStatementStatements.addIfNotNullAndNotBlock(it)
                                    }
                            }
                            super.visitExpression(expression)
                        }
                    })
                    if (target !in lastStatementStatements) {
                        return
                    }
                    lastStatementStatements
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
                            if (!targetOccurrenceAdded) {
                                when (relevantFunction) {
                                    is KtFunctionLiteral -> relevantFunction.parentOfTypes(KtCallExpression::class)?.calleeExpression
                                    is KtNamedFunction -> relevantFunction.nameIdentifier
                                    else -> null
                                }?.let {
                                    targetOccurrenceAdded = true
                                    addOccurrence(it)
                                }
                            }
                            return
                        }
                    }

                    super.visitExpression(expression)
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

                private fun visitReturnOrThrow(expression: KtExpression) {
                    if (getRelevantDeclaration(expression) == relevantFunction) {
                        addOccurrence(expression)
                    }
                }

                override fun visitReturnExpression(expression: KtReturnExpression) {
                    visitReturnOrThrow(expression)
                }

                override fun visitThrowExpression(expression: KtThrowExpression) {
                    visitReturnOrThrow(expression)
                }
            })
        }

        override fun highlightReferences() = highlightReferences
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