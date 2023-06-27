// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.types.KtClassErrorType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.Variance

data class ConvertToBlockBodyContext(
    val returnTypeIsUnit: Boolean,
    val returnTypeIsNothing: Boolean,
    val returnTypeString: String,
    val bodyTypeIsUnit: Boolean,
    val bodyTypeIsNothing: Boolean,
    val reformat: Boolean,
    val shortenReferences: (KtTypeReference) -> Unit
)

object ConvertToBlockBodyUtils {
    fun isConvertibleByPsi(element: KtDeclarationWithBody): Boolean =
        (element is KtNamedFunction || element is KtPropertyAccessor) && !element.hasBlockBody() && element.hasBody()

    context(KtAnalysisSession)
    fun createContext(
        declaration: KtDeclarationWithBody,
        shortenReferences: (KtTypeReference) -> Unit,
        reformat: Boolean,
    ): ConvertToBlockBodyContext? {
        if (!isConvertibleByPsi(declaration)) return null

        val body = declaration.bodyExpression ?: return null

        val returnType = declaration.getReturnKtType().approximateToSuperPublicDenotableOrSelf(approximateLocalTypes = true)
        if (returnType is KtClassErrorType && declaration is KtNamedFunction && !declaration.hasDeclaredReturnType()) {
            return null
        }

        val bodyType = body.getKtType() ?: return null

        return ConvertToBlockBodyContext(
            returnTypeIsUnit = returnType.isUnit,
            returnTypeIsNothing = returnType.isNothing,
            returnTypeString = returnType.render(position = Variance.OUT_VARIANCE),
            bodyTypeIsUnit = bodyType.isUnit,
            bodyTypeIsNothing = bodyType.isNothing,
            reformat = reformat,
            shortenReferences = shortenReferences
        )
    }

    fun convert(element: KtDeclarationWithBody, context: ConvertToBlockBodyContext) {
        val body = element.bodyExpression ?: return

        element.setTypeReferenceIfNeeded(context)

        val prevComments = body.comments(next = false)
        val nextComments = body.comments(next = true)
        val returnsValue = element.returnsValue(context)
        val newBody = generateBody(body, prevComments, nextComments, context, returnsValue)

        element.equalsToken?.delete()
        prevComments.filterIsInstance<PsiComment>().forEach { it.delete() }
        nextComments.forEach { it.delete() }
        val replaced = body.replace(newBody)
        if (context.reformat) element.containingKtFile.adjustLineIndent(replaced.startOffset, replaced.endOffset)
    }

    private fun KtDeclarationWithBody.setTypeReferenceIfNeeded(context: ConvertToBlockBodyContext) {
        fun KtCallableDeclaration.setTypeReference() {
            val addedTypeReference = setTypeReference(KtPsiFactory(project).createType(context.returnTypeString))
            if (addedTypeReference != null) {
                context.shortenReferences(addedTypeReference)
            }
        }

        when (this) {
            is KtNamedFunction -> {
                if (!hasDeclaredReturnType() && !context.returnTypeIsUnit) {
                    this.setTypeReference()
                }
            }

            is KtPropertyAccessor -> {
                val parent = parent
                if (parent is KtProperty && parent.typeReference == null) {
                    parent.setTypeReference()
                }
            }
        }
    }

    private fun KtExpression.comments(next: Boolean): List<PsiElement> {
        fun Sequence<PsiElement>.hasComment(): Boolean = any { it is PsiComment }

        return siblings(forward = next, withItself = false)
            .takeWhile { it is PsiWhiteSpace || it is PsiComment }
            .takeIf { it.hasComment() }
            .orEmpty()
            .toList()
    }

    private fun KtDeclarationWithBody.returnsValue(context: ConvertToBlockBodyContext): Boolean = when (this) {
        is KtNamedFunction -> !context.returnTypeIsUnit && !context.returnTypeIsNothing
        is KtPropertyAccessor -> isGetter
        else -> throw RuntimeException("Unknown declaration type: $this")
    }

    private fun generateBody(
        body: KtExpression,
        prevComments: List<PsiElement>,
        nextComments: List<PsiElement>,
        context: ConvertToBlockBodyContext,
        returnsValue: Boolean,
    ): KtExpression {
        val factory = KtPsiFactory(body.project)
        if (context.bodyTypeIsUnit && body is KtNameReferenceExpression) return factory.createEmptyBody()

        val needReturn = returnsValue && (!context.bodyTypeIsUnit && !context.bodyTypeIsNothing)
        val newBody = if (needReturn) {
            val annotatedExpr = body as? KtAnnotatedExpression
            val returnedExpr = annotatedExpr?.baseExpression ?: body
            val block = factory.createSingleStatementBlock(factory.createExpressionByPattern("return $0", returnedExpr))
            val statement = block.firstStatement
            annotatedExpr?.annotationEntries?.forEach {
                block.addBefore(it, statement)
                block.addBefore(factory.createNewLine(), statement)
            }
            block
        } else {
            factory.createSingleStatementBlock(body)
        }

        prevComments
            .dropWhile { it is PsiWhiteSpace }
            .forEach { newBody.addAfter(it, newBody.lBrace) }
        nextComments
            .reversed()
            .forEach { newBody.addAfter(it, newBody.firstStatement) }

        return newBody
    }
}
