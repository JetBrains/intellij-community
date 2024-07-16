// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.siblings
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.isLineBreak
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

    // Check if function has block with single non-empty KtReturnExpression or the block is empty
    val statements = bodyBlockExpression?.statements ?: return true
    if (statements.isEmpty()) return true

    val returnedExpression = statements.singleOrNull() ?: return false

    // Check if the returnedExpression actually always returns (early return is possible)
    // TODO: take into consideration other cases (???)
    val expression = (returnedExpression as? KtReturnExpression)?.returnedExpression ?: returnedExpression
    return !expression.anyDescendantOfType<KtReturnExpression>(
        canGoInside = { it !is KtFunctionLiteral && it !is KtNamedFunction && it !is KtPropertyAccessor }
    )
}

fun KtDeclarationWithBody.replaceWithExpressionBodyPreservingComments(specifyType: Boolean = false): KtExpression {
    val bodyBlock = bodyBlockExpression ?: return this
    val returnedExpression = singleReturnExpressionOrNull?.returnedExpression ?: bodyBlockExpression?.statements?.singleOrNull() ?: KtPsiFactory(project).createExpression("Unit")

    val factory = KtPsiFactory(project)
    if (specifyType && !hasDeclaredReturnType() && this is KtNamedFunction && bodyBlock.statements.isNotEmpty()) {
        val colon = addBefore(factory.createColon(), bodyBlockExpression)
        addAfter(factory.createWhiteSpace(), colon)
        addBefore(factory.createType("Unit"), bodyBlockExpression)
    }

    val prevComments = bodyBlock.lBrace
        ?.siblings(withSelf = false)
        ?.takeWhile { it is PsiWhiteSpace || it is PsiComment }
        .orEmpty()
    val newLineRequiredAfterEq = prevComments.firstOrNull().isLineBreak() && prevComments.any { it is PsiComment }

    val commentSaver = CommentSaver(bodyBlock)

    val eq = addBefore(factory.createEQ(), bodyBlockExpression)
    if (newLineRequiredAfterEq) {
        addBefore(factory.createNewLine(), bodyBlock)
    }
    addAfter(factory.createWhiteSpace(), eq)

    val newBody = bodyBlock.replaced(returnedExpression)

    commentSaver.restore(newBody)

    return newBody
}

val KtDeclarationWithBody.singleReturnExpressionOrNull: KtReturnExpression?
    get() = bodyBlockExpression?.statements?.singleOrNull() as? KtReturnExpression

