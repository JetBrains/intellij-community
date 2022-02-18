// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.api.applicator.HLApplicatorInput
import org.jetbrains.kotlin.idea.api.applicator.applicator
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.fir.api.AbstractHLIntention
import org.jetbrains.kotlin.idea.fir.api.applicator.HLApplicabilityRange
import org.jetbrains.kotlin.idea.fir.api.applicator.HLApplicatorInputProvider
import org.jetbrains.kotlin.idea.fir.api.applicator.applicabilityRanges
import org.jetbrains.kotlin.idea.fir.api.applicator.inputProvider
import org.jetbrains.kotlin.idea.inspections.findExistingEditor
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class HLUseExpressionBodyIntention : AbstractHLIntention<KtDeclarationWithBody, HLUseExpressionBodyIntention.Input>(
    KtDeclarationWithBody::class, applicator
) {

    class Input : HLApplicatorInput

    override val applicabilityRange: HLApplicabilityRange<KtDeclarationWithBody> = applicabilityRanges {
        val returnExpression =
            it.bodyBlockExpression?.singleStatementOrNull as? KtReturnExpression ?: return@applicabilityRanges emptyList()
        if (returnExpression.returnedExpression == null) return@applicabilityRanges emptyList()
        listOf(TextRange(0, returnExpression.returnKeyword.endOffset - it.startOffset), it.rBraceOffSetTextRange)
    }

    override val inputProvider: HLApplicatorInputProvider<KtDeclarationWithBody, Input> = inputProvider { Input() }

    override fun skipProcessingFurtherElementsAfter(element: PsiElement) = false

    companion object {

        val applicator = applicator<KtDeclarationWithBody, Input> {
            familyAndActionName(KotlinBundle.lazyMessage(("convert.body.to.expression")))
            isApplicableByPsi { declaration ->

                // Check if either property accessor or named function
                if (declaration !is KtNamedFunction && declaration !is KtPropertyAccessor) return@isApplicableByPsi false

                //Check if a named function has explicit type
                if (declaration is KtNamedFunction && !declaration.hasDeclaredReturnType()) return@isApplicableByPsi false

                // Checking if function has block with single statement
                val blockExpression = declaration.bodyBlockExpression ?: return@isApplicableByPsi false
                if (blockExpression.singleStatementOrNull == null) return@isApplicableByPsi false

                // Checking if the single statement is nonempty KtReturnExpression
                val returnExpression = blockExpression.singleStatementOrNull as? KtReturnExpression ?: return@isApplicableByPsi false
                if (returnExpression.returnedExpression == null ||
                    returnExpression.returnedExpression?.anyDescendantOfType<KtReturnExpression>() == true)
                    return@isApplicableByPsi false

                true
            }
            applyTo { declaration, _ ->
                declaration.replaceBodyWithSingleExpression()
            }
        }

        private fun KtDeclarationWithBody.replaceBodyWithSingleExpression() {

            val kotlinFactory = KtPsiFactory(this)
            val newFunctionBody = commentSafeReplace()
            findExistingEditor().let {
                it?.rightMarginCorrect(this, newFunctionBody, kotlinFactory)

                if (this is KtNamedFunction)
                    it?.selectFunctionColonType(this)
            }
        }

        private fun KtDeclarationWithBody.commentSafeReplace(): KtExpression {
            val returnedExpression = (bodyBlockExpression?.singleStatementOrNull as? KtReturnExpression)?.returnedExpression ?: return this

            val commentSaver = CommentSaver(bodyBlockExpression!!)

            val factory = KtPsiFactory(this)
            val eq = addBefore(factory.createEQ(), bodyBlockExpression)
            addAfter(factory.createWhiteSpace(), eq)

            // Safe to use "!!" as isApplicable checks for its presence
            val newBody = bodyBlockExpression!!.replaced(returnedExpression)

            commentSaver.restore(newBody)

            return newBody
        }

        private fun Editor.rightMarginCorrect(functionDecl: KtDeclarationWithBody, newFunctionBody: KtExpression, kotlinFactory: KtPsiFactory) {
            val startOffset = newFunctionBody.startOffset
            val startLine = document.getLineNumber(startOffset)
            val rightMargin = settings.getRightMargin(project)
            if (document.getLineEndOffset(startLine) - document.getLineStartOffset(startLine) >= rightMargin) {
                functionDecl.addBefore(kotlinFactory.createNewLine(), newFunctionBody)
            }
        }

        private fun Editor.selectFunctionColonType(newFunctionBody: KtNamedFunction) {
            newFunctionBody.run {
                selectionModel.setSelection(colon!!.startOffset, typeReference!!.endOffset)
                caretModel.moveToOffset(typeReference!!.endOffset)
            }
        }

        private val KtBlockExpression.singleStatementOrNull: KtExpression?
            get() = if (this.statements.size != 1) null else this.firstStatement

        private val KtDeclarationWithBody.rBraceOffSetTextRange: TextRange
            get() = bodyBlockExpression!!.let {
                TextRange(
                    it.rBrace?.startOffset!! - startOffset, it.rBrace!!.endOffset - startOffset
                )
            }
    }
}