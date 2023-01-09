// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.adjustLineIndent
import org.jetbrains.kotlin.idea.core.setType
import org.jetbrains.kotlin.idea.util.resultingWhens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.isNullabilityFlexible
import org.jetbrains.kotlin.types.typeUtil.isNothing
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable

class ConvertToBlockBodyIntention : SelfTargetingIntention<KtDeclarationWithBody>(
    KtDeclarationWithBody::class.java,
    KotlinBundle.lazyMessage("convert.to.block.body")
) {
    override fun isApplicableTo(element: KtDeclarationWithBody, caretOffset: Int): Boolean {
        if (element is KtFunctionLiteral || element.hasBlockBody() || !element.hasBody()) return false

        when (element) {
            is KtNamedFunction -> {
                val returnType = element.returnType() ?: return false
                if (!element.hasDeclaredReturnType() && returnType.isError) return false// do not convert when type is implicit and unknown
                return true
            }

            is KtPropertyAccessor -> return true

            else -> error("Unknown declaration type: $element")
        }
    }

    override fun skipProcessingFurtherElementsAfter(element: PsiElement) =
        element is KtDeclaration || super.skipProcessingFurtherElementsAfter(element)

    override fun applyTo(element: KtDeclarationWithBody, editor: Editor?) {
        convert(element, true)
    }

    companion object {
        fun convert(declaration: KtDeclarationWithBody, withReformat: Boolean = false): KtDeclarationWithBody {
            val body = declaration.bodyExpression!!
            val prevComments = body.comments(next = false)
            val nextComments = body.comments(next = true)

            fun generateBody(returnsValue: Boolean): KtExpression {
                val bodyType = body.analyze().getType(body)
                val psiFactory = KtPsiFactory(declaration.project)
                if (bodyType != null && bodyType.isUnit() && body is KtNameReferenceExpression) return psiFactory.createEmptyBody()
                val unitWhenAsResult = (bodyType == null || bodyType.isUnit()) && body.resultingWhens().isNotEmpty()
                val needReturn = returnsValue && (bodyType == null || (!bodyType.isUnit() && !bodyType.isNothing()))
                val newBody = if (needReturn || unitWhenAsResult) {
                    val annotatedExpr = body as? KtAnnotatedExpression
                    val returnedExpr = annotatedExpr?.baseExpression ?: body
                    val block = psiFactory.createSingleStatementBlock(psiFactory.createExpressionByPattern("return $0", returnedExpr))
                    val statement = block.firstStatement
                    annotatedExpr?.annotationEntries?.forEach {
                        block.addBefore(it, statement)
                        block.addBefore(psiFactory.createNewLine(), statement)
                    }
                    block
                } else {
                    psiFactory.createSingleStatementBlock(body)
                }
                prevComments
                    .dropWhile { it is PsiWhiteSpace }
                    .forEach { newBody.addAfter(it, newBody.lBrace) }
                nextComments
                    .reversed()
                    .forEach { newBody.addAfter(it, newBody.firstStatement) }
                return newBody
            }

            val newBody = when (declaration) {
                is KtNamedFunction -> {
                    val returnType = declaration.returnType()!!
                    if (!declaration.hasDeclaredReturnType() && !returnType.isUnit()) {
                        declaration.setType(returnType)
                    }
                    generateBody(!returnType.isUnit() && !returnType.isNothing())
                }

                is KtPropertyAccessor -> {
                    val parent = declaration.parent
                    if (parent is KtProperty && parent.typeReference == null) {
                        val descriptor = parent.resolveToDescriptorIfAny()
                        (descriptor as? CallableDescriptor)?.returnType?.let { parent.setType(it) }
                    }

                    generateBody(declaration.isGetter)
                }

                else -> throw RuntimeException("Unknown declaration type: $declaration")
            }

            declaration.equalsToken!!.delete()
            prevComments.filterIsInstance<PsiComment>().forEach { it.delete() }
            nextComments.forEach { it.delete() }
            val replaced = body.replace(newBody)
            if (withReformat) {
                declaration.containingKtFile.adjustLineIndent(replaced.startOffset, replaced.endOffset)
            }
            return declaration
        }

        private fun KtNamedFunction.returnType(): KotlinType? {
            val descriptor = resolveToDescriptorIfAny()
            val returnType = descriptor?.returnType ?: return null
            if (returnType.isNullabilityFlexible()
                && descriptor.overriddenDescriptors.firstOrNull()?.returnType?.isMarkedNullable == false
            ) return returnType.makeNotNullable()
            return returnType
        }

        private fun KtExpression.comments(next: Boolean): List<PsiElement> = siblings(forward = next, withItself = false)
            .takeWhile { it is PsiWhiteSpace || it is PsiComment }
            .takeIf { it.hasComment() }
            .orEmpty()
            .toList()

        private fun Sequence<PsiElement>.hasComment(): Boolean = any { it is PsiComment }
    }
}
