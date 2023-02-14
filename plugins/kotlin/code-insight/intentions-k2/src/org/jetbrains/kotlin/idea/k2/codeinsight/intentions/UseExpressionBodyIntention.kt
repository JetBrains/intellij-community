// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class UseExpressionBodyIntention :
    AbstractKotlinApplicableIntention<KtDeclarationWithBody>(KtDeclarationWithBody::class) {

    override fun getFamilyName(): String = KotlinBundle.message("convert.body.to.expression")
    override fun getActionName(element: KtDeclarationWithBody): String = familyName

    override fun getApplicabilityRange() = applicabilityRanges { declaration: KtDeclarationWithBody ->
        val returnExpression = declaration.singleReturnExpressionOrNull ?: return@applicabilityRanges emptyList()
        val resultTextRanges = mutableListOf(TextRange(0, returnExpression.returnKeyword.endOffset - declaration.startOffset))

        // Adding applicability to the end of the declaration block
        val rBraceTextRange = declaration.rBraceOffSetTextRange ?: return@applicabilityRanges resultTextRanges
        resultTextRanges.add(rBraceTextRange)

        resultTextRanges
    }

    override fun isApplicableByPsi(element: KtDeclarationWithBody): Boolean {
        // Check if either property accessor or named function
        if (element !is KtNamedFunction && element !is KtPropertyAccessor) return false

        // Check if a named function has explicit type
        if (element is KtNamedFunction && !element.hasDeclaredReturnType()) return false

        // Check if function has block with single non-empty KtReturnExpression
        val returnedExpression = element.singleReturnedExpressionOrNull ?: return false

        // Check if the returnedExpression actually always returns (early return is possible)
        // TODO: take into consideration other cases (???)
        if (returnedExpression.anyDescendantOfType<KtReturnExpression>(
                canGoInside = { it !is KtFunctionLiteral && it !is KtNamedFunction && it !is KtPropertyAccessor }
        )) return false

        return true
    }

    override fun apply(element: KtDeclarationWithBody, project: Project, editor: Editor?) {
        if (editor == null) return
        val newFunctionBody = element.replaceWithPreservingComments()
        editor.correctRightMargin(element, newFunctionBody)
        if (element is KtNamedFunction) editor.selectFunctionColonType(element)
    }

    override fun skipProcessingFurtherElementsAfter(element: PsiElement) = false
}

private fun KtDeclarationWithBody.replaceWithPreservingComments(): KtExpression {
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

/**
 * This function guarantees that the function with its old body replaced by returned expression
 * will have this expression placed on the next line to the function's signature or property accessor's 'get() =' statement
 * in case it goes beyond IDEA editor's right margin
 * @param[declaration] the PSI element used as an anchor, as no indexes are built for newly generated body yet
 * @param[newBody] the new "= <returnedExpression>" like body, which replaces the old one
 */
private fun Editor.correctRightMargin(declaration: KtDeclarationWithBody, newBody: KtExpression) {
    val kotlinFactory = KtPsiFactory(declaration.project)
    val startOffset = newBody.startOffset
    val startLine = document.getLineNumber(startOffset)
    val rightMargin = settings.getRightMargin(project)
    if (document.getLineEndOffset(startLine) - document.getLineStartOffset(startLine) >= rightMargin) {
        declaration.addBefore(kotlinFactory.createNewLine(), newBody)
    }
}

private fun Editor.selectFunctionColonType(newFunctionBody: KtNamedFunction) {
    val colon = newFunctionBody.colon ?: return
    val typeReference = newFunctionBody.typeReference ?: return
    selectionModel.setSelection(colon.startOffset, typeReference.endOffset)
    caretModel.moveToOffset(typeReference.endOffset)
}

private val KtDeclarationWithBody.singleReturnExpressionOrNull: KtReturnExpression?
    get() = bodyBlockExpression?.statements?.singleOrNull() as? KtReturnExpression

private val KtDeclarationWithBody.singleReturnedExpressionOrNull: KtExpression?
    get() = singleReturnExpressionOrNull?.returnedExpression

private val KtDeclarationWithBody.rBraceOffSetTextRange: TextRange?
    get() {
        val rightBlockBodyBrace = bodyBlockExpression?.rBrace ?: return null
        return rightBlockBodyBrace.textRange.shiftLeft(startOffset)
    }