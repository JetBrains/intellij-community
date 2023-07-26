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
        val returnOrThrow = when (val parent = target.parent) {
            is KtReturnExpression, is KtThrowExpression -> parent
            is KtLabelReferenceExpression ->
                PsiTreeUtil.getParentOfType(
                    target, KtReturnExpression::class.java, KtThrowExpression::class.java, KtFunction::class.java
                )?.takeUnless { it is KtFunction }
            is KtIfExpression, is KtWhenExpression, is KtBlockExpression -> null
            is KtExpression -> {
                var node: PsiElement? = parent
                var functionLiteral: KtFunctionLiteral? = null
                val expressions = hashSetOf<KtExpression>()
                while (node != null) {
                    val nextParent = node.parent
                    when(node) {
                        is PsiFile, is KtNamedFunction, is KtClassOrObject -> break
                        is KtFunctionLiteral -> {
                            functionLiteral = node
                            break
                        }
                        is KtExpression -> expressions += node
                        else -> {
                            if ((nextParent is KtIfExpression || nextParent is KtWhenExpression || nextParent is KtBlockExpression) &&
                                !KtPsiUtil.isStatementContainer(node)) {
                                break
                            }
                        }
                    }
                    node = nextParent
                }
                functionLiteral?.let {
                    val lastStatement = it.bodyExpression?.statements?.lastOrNull()
                    parent.takeIf { lastStatement in expressions }
                }
            }
            else -> null
        } as? KtExpression ?: return null
        return OnExitUsagesHandler(editor, file, returnOrThrow)
    }

    private fun getOnLambdaCallUsageHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        if (target !is LeafPsiElement
            || target.elementType !is KtToken // do not trigger loading of KtTokens in Java
            || target.elementType != KtTokens.IDENTIFIER) {
            return null
        }

        val refExpr = target.parent as? KtNameReferenceExpression ?: return null
        val call = refExpr.parent as? KtCallExpression ?: return null
        if (call.calleeExpression != refExpr) return null

        val lambda = call.lambdaArguments.singleOrNull() ?: return null
        val literal = lambda.getLambdaExpression()?.functionLiteral ?: return null

        return OnExitUsagesHandler(editor, file, literal, highlightReferences = true)
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
                if (target is KtFunctionLiteral) {
                    target
                } else {
                    getRelevantDeclaration(target)
                }

            var targetOccurrenceAdded = false
            if (target is KtReturnExpression || target is KtThrowExpression) {
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

            val lastLambdaStatementExpressions =
                if (relevantFunction is KtFunctionLiteral) {
                    buildSet {
                        val set = this
                        set += relevantFunction
                        relevantFunction.acceptChildren(object : KtVisitorVoid() {
                            override fun visitKtElement(element: KtElement) {
                                ProgressIndicatorProvider.checkCanceled()
                                element.acceptChildren(this)
                            }

                            override fun visitExpression(expression: KtExpression) {
                                when(expression) {
                                    is KtBinaryExpression -> {
                                        set.addIfNotNullAndNotBlock(expression.left)
                                        set.addIfNotNullAndNotBlock(expression.right)
                                    }
                                    is KtCallExpression -> {
                                        set.addIfNotNullAndNotBlock(expression.calleeExpression)
                                    }
                                    is KtBlockExpression -> set.addIfNotNullAndNotBlock(expression.statements.lastOrNull())
                                    is KtIfExpression -> {
                                        set.addIfNotNullAndNotBlock(expression.then)
                                        set.addIfNotNullAndNotBlock(expression.`else`)
                                    }
                                    is KtWhenExpression ->
                                        expression.entries.map { it.expression }.forEach {
                                            set.addIfNotNullAndNotBlock(it)
                                        }
                                }
                                super.visitExpression(expression)
                            }
                        })
                    }
                } else {
                    emptySet()
                }

            if (relevantFunction is KtFunctionLiteral && target !in lastLambdaStatementExpressions) {
                return
            }

            relevantFunction?.accept(object : KtVisitorVoid() {
                override fun visitKtElement(element: KtElement) {
                    ProgressIndicatorProvider.checkCanceled()
                    element.acceptChildren(this)
                }

                override fun visitExpression(expression: KtExpression) {
                    if (relevantFunction is KtFunctionLiteral) {
                        if (occurrenceForFunctionLiteralReturnExpression(expression, expression in lastLambdaStatementExpressions)) {
                            if (!targetOccurrenceAdded) {
                                relevantFunction.parentOfTypes(KtCallExpression::class)?.calleeExpression?.let {
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

        private fun MutableSet<Any>.addIfNotNullAndNotBlock(element: PsiElement?) {
            addIfNotNull(element.takeUnless { it is KtBlockExpression })
        }

        override fun highlightReferences() = highlightReferences
    }
}