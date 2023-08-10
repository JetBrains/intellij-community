// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactoryBase
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.Consumer
import org.jetbrains.kotlin.idea.codeinsight.utils.getFunctionLiteralByImplicitLambdaParameter
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

class KotlinHighlightImplicitItHandlerFactory : HighlightUsagesHandlerFactoryBase() {
    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile, target: PsiElement): HighlightUsagesHandlerBase<*>? {
        if (target !is LeafPsiElement
            || target.elementType !is KtToken // do not trigger loading of KtTokens in Java
            || target.elementType != KtTokens.IDENTIFIER) {
            return null
        }

        val refExpr = target.parent as? KtNameReferenceExpression ?: return null
        val lambda = refExpr.getFunctionLiteralByImplicitLambdaParameter() ?: return null
        return object : HighlightUsagesHandlerBase<KtNameReferenceExpression>(editor, file) {
            override fun getTargets() = listOf(refExpr)

            override fun selectTargets(
                targets: List<KtNameReferenceExpression>,
                selectionConsumer: Consumer<in List<KtNameReferenceExpression>>
            ) = selectionConsumer.consume(targets)

            override fun computeUsages(targets: List<KtNameReferenceExpression>) {
                lambda.accept(
                    object : KtTreeVisitorVoid() {
                        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                            if (expression is KtNameReferenceExpression && expression.getFunctionLiteralByImplicitLambdaParameter() == lambda) {
                                addOccurrence(expression)
                            }
                        }
                    }
                )
            }
        }
    }
}