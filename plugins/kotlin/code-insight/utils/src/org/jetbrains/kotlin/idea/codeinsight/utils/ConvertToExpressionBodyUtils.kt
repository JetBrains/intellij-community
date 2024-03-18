// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType

fun KtDeclarationWithBody.isConvertableToExpressionBody(): Boolean {
    // Check if either property accessor or named function
    if (this !is KtNamedFunction && this !is KtPropertyAccessor) return false

    // Check if a named function has explicit type
    if (this is KtNamedFunction && !hasDeclaredReturnType()) return false

    // Check if function has block with single non-empty KtReturnExpression
    val returnedExpression = singleReturnedExpressionOrNull ?: return false

    // Check if the returnedExpression actually always returns (early return is possible)
    // TODO: take into consideration other cases (???)
    return !returnedExpression.anyDescendantOfType<KtReturnExpression>(
        canGoInside = { it !is KtFunctionLiteral && it !is KtNamedFunction && it !is KtPropertyAccessor }
    )
}

fun KtDeclarationWithBody.replaceWithExpressionBodyPreservingComments(): KtExpression {
    val bodyBlock = bodyBlockExpression ?: return this
    val returnedExpression = singleReturnedExpressionOrNull ?: return this

    val commentSaver = CommentSaver(bodyBlock)

    val factory = KtPsiFactory(project)
    val eq = addBefore(factory.createEQ(), bodyBlockExpression)
    addAfter(factory.createWhiteSpace(), eq)

    val newBody = bodyBlock.replaced(returnedExpression)

    commentSaver.restore(newBody)

    return newBody
}

val KtDeclarationWithBody.singleReturnExpressionOrNull: KtReturnExpression?
    get() = bodyBlockExpression?.statements?.singleOrNull() as? KtReturnExpression

private val KtDeclarationWithBody.singleReturnedExpressionOrNull: KtExpression?
    get() = singleReturnExpressionOrNull?.returnedExpression
