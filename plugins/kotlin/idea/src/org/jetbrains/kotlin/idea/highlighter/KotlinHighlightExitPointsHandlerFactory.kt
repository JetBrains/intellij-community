// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Consumer
import org.jetbrains.kotlin.idea.util.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunction
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsResultOfLambda
import org.jetbrains.kotlin.resolve.inline.InlineUtil
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class KotlinHighlightExitPointsHandlerFactory : HighlightUsagesHandlerFactoryBase() {
    companion object {
        private fun getOnReturnOrThrowUsageHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
            val returnOrThrow = when (val parent = target.parent) {
                is KtReturnExpression, is KtThrowExpression -> parent
                is KtLabelReferenceExpression ->
                    PsiTreeUtil.getParentOfType(
                        target, KtReturnExpression::class.java, KtThrowExpression::class.java, KtFunction::class.java
                    )?.takeUnless { it is KtFunction }
                else -> null
            } as? KtExpression ?: return null
            return OnExitUsagesHandler(editor, file, returnOrThrow)
        }

        private fun getOnLambdaCallUsageHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
            if (target !is LeafPsiElement || target.elementType != KtTokens.IDENTIFIER) {
                return null
            }

            val refExpr = target.parent as? KtNameReferenceExpression ?: return null
            val call = refExpr.parent as? KtCallExpression ?: return null
            if (call.calleeExpression != refExpr) return null

            val lambda = call.lambdaArguments.singleOrNull() ?: return null
            val literal = lambda.getLambdaExpression()?.functionLiteral ?: return null

            return OnExitUsagesHandler(editor, file, literal, highlightReferences = true)
        }
    }

    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        return getOnReturnOrThrowUsageHandler(editor, file, target)
            ?: getOnLambdaCallUsageHandler(editor, file, target)
    }

    private class OnExitUsagesHandler(editor: Editor, file: PsiFile, val target: KtExpression, val highlightReferences: Boolean = false) :
        HighlightUsagesHandlerBase<PsiElement>(editor, file) {

        override fun getTargets() = listOf(target)

        override fun selectTargets(targets: MutableList<out PsiElement>, selectionConsumer: Consumer<in MutableList<out PsiElement>>) {
            selectionConsumer.consume(targets)
        }

        override fun computeUsages(targets: MutableList<out PsiElement>) {
            val relevantFunction: KtDeclarationWithBody? =
                if (target is KtFunctionLiteral) {
                    target
                } else {
                    target.getRelevantDeclaration()
                }

            if (target is KtReturnExpression || target is KtThrowExpression) {
                when (relevantFunction) {
                    is KtNamedFunction -> (relevantFunction.nameIdentifier ?: relevantFunction.funKeyword)?.let { addOccurrence(it) }
                    is KtFunctionLiteral -> relevantFunction.getStrictParentOfType<KtLambdaArgument>()
                        ?.getStrictParentOfType<KtCallExpression>()
                        ?.calleeExpression
                        ?.let { addOccurrence(it) }
                }
            }

            relevantFunction?.accept(object : KtVisitorVoid() {
                override fun visitKtElement(element: KtElement) {
                    ProgressIndicatorProvider.checkCanceled()
                    element.acceptChildren(this)
                }

                override fun visitExpression(expression: KtExpression) {
                    if (relevantFunction is KtFunctionLiteral) {
                        if (occurrenceForFunctionLiteralReturnExpression(expression)) {
                            return
                        }
                    }

                    super.visitExpression(expression)
                }

                private fun occurrenceForFunctionLiteralReturnExpression(expression: KtExpression): Boolean {
                    if (!KtPsiUtil.isStatement(expression)) return false

                    if (expression is KtIfExpression || expression is KtWhenExpression || expression is KtBlockExpression) {
                        return false
                    }

                    val bindingContext = expression.safeAnalyzeNonSourceRootCode(BodyResolveMode.FULL)
                    if (bindingContext == BindingContext.EMPTY || !expression.isUsedAsResultOfLambda(bindingContext)) {
                        return false
                    }

                    if (expression.getRelevantDeclaration() != relevantFunction) {
                        return false
                    }

                    addOccurrence(expression)
                    return true
                }

                private fun visitReturnOrThrow(expression: KtExpression) {
                    if (expression.getRelevantDeclaration() == relevantFunction) {
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
}

private fun KtExpression.getRelevantDeclaration(): KtDeclarationWithBody? {
    if (this is KtReturnExpression) {
        val targetFunction = getTargetFunction(safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)) as? KtDeclarationWithBody
        if (targetFunction != null) return targetFunction
    }

    if (this is KtThrowExpression || this is KtReturnExpression) {
        for (parent in parents) {
            if (parent is KtDeclarationWithBody) {
                if (parent is KtPropertyAccessor) {
                    return parent
                }

                if (InlineUtil.canBeInlineArgument(parent) &&
                    !InlineUtil.isInlinedArgument(parent as KtFunction, parent.safeAnalyzeNonSourceRootCode(BodyResolveMode.FULL), false)
                ) {
                    return parent
                }
            }
        }

        return null
    }

    return parents.filterIsInstance<KtDeclarationWithBody>().firstOrNull()
}