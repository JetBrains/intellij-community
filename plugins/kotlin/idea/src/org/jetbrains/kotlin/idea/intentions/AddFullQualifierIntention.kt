// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.createSmartPointer
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.prevLeaf
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.quoteSegmentsIfNeeded
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.core.thisOrParentIsRoot
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension

class AddFullQualifierIntention : SelfTargetingIntention<KtNameReferenceExpression>(
    KtNameReferenceExpression::class.java,
    KotlinBundle.lazyMessage("add.full.qualifier")
), LowPriorityAction {
    override fun isApplicableTo(element: KtNameReferenceExpression, caretOffset: Int): Boolean = isApplicableTo(
        referenceExpression = element,
        contextDescriptor = null,
    )

    override fun applyTo(element: KtNameReferenceExpression, editor: Editor?) {
        val fqName = element.findSingleDescriptor()?.importableFqName ?: return
        applyTo(element, fqName)
    }

    override fun startInWriteAction(): Boolean = false

    companion object {
        fun isApplicableTo(referenceExpression: KtNameReferenceExpression, contextDescriptor: DeclarationDescriptor?): Boolean {
            if (referenceExpression.parent is KtInstanceExpressionWithLabel) return false

            val prevElement = referenceExpression.prevElementWithoutSpacesAndComments()
            if (prevElement.elementType == KtTokens.DOT) return false
            val resultDescriptor = contextDescriptor ?: referenceExpression.findSingleDescriptor() ?: return false
            if (resultDescriptor.isExtension || resultDescriptor.isInRoot) return false
            if (prevElement.elementType == KtTokens.COLONCOLON) {
                if (resultDescriptor.isTopLevelCallable) return false
                val prevSibling = prevElement?.getPrevSiblingIgnoringWhitespaceAndComments()
                if (prevSibling is KtNameReferenceExpression || prevSibling is KtDotQualifiedExpression) return false
            }

            return true
        }

        fun applyTo(referenceExpression: KtNameReferenceExpression, fqName: FqName): KtElement {
            val qualifier = fqName.parent().quoteSegmentsIfNeeded()
            return referenceExpression.project.executeWriteCommand(KotlinBundle.message("add.full.qualifier"), groupId = null) {
                val psiFactory = KtPsiFactory(referenceExpression)
                when (val parent = referenceExpression.parent) {
                    is KtCallableReferenceExpression -> addOrReplaceQualifier(psiFactory, parent, qualifier)
                    is KtCallExpression -> replaceExpressionWithDotQualifier(psiFactory, parent, qualifier)
                    is KtUserType -> addQualifierToType(psiFactory, parent, qualifier)
                    else -> replaceExpressionWithQualifier(psiFactory, referenceExpression, fqName)
                }
            }
        }

        fun addQualifiersRecursively(root: KtElement): KtElement {
            if (root is KtNameReferenceExpression) return applyIfApplicable(root) ?: root

            root.descendantsOfType<KtNameReferenceExpression>()
                .map { it.createSmartPointer() }
                .toList()
                .asReversed()
                .forEach {
                    it.element?.let(::applyIfApplicable)
                }

            return root
        }

        private fun applyIfApplicable(referenceExpression: KtNameReferenceExpression): KtElement? {
            val descriptor = referenceExpression.findSingleDescriptor() ?: return null
            val fqName = descriptor.importableFqName ?: return null
            if (!isApplicableTo(referenceExpression, descriptor)) return null
            return applyTo(referenceExpression, fqName)
        }
    }
}

private fun addOrReplaceQualifier(factory: KtPsiFactory, expression: KtCallableReferenceExpression, qualifier: String): KtElement {
    val receiver = expression.receiverExpression
    return if (receiver != null) {
        replaceExpressionWithDotQualifier(factory, receiver, qualifier)
    } else {
        val qualifierExpression = factory.createExpression(qualifier)
        expression.addBefore(qualifierExpression, expression.firstChild) as KtElement
    }
}

private fun replaceExpressionWithDotQualifier(psiFactory: KtPsiFactory, expression: KtExpression, qualifier: String): KtElement {
    val expressionWithQualifier = psiFactory.createExpressionByPattern("$0.$1", qualifier, expression)
    return expression.replaced(expressionWithQualifier)
}

private fun addQualifierToType(psiFactory: KtPsiFactory, userType: KtUserType, qualifier: String): KtElement {
    val typeWithQualifier = psiFactory.createType("$qualifier.${userType.text}")
    return userType.parent.replaced(typeWithQualifier)
}

private fun replaceExpressionWithQualifier(
    psiFactory: KtPsiFactory,
    referenceExpression: KtNameReferenceExpression,
    fqName: FqName
): KtElement {
    val expressionWithQualifier = psiFactory.createExpression(fqName.asString())
    return referenceExpression.replaced(expressionWithQualifier)
}

private val DeclarationDescriptor.isInRoot: Boolean get() = importableFqName?.thisOrParentIsRoot() != false

private val DeclarationDescriptor.isTopLevelCallable: Boolean get() = this is CallableMemberDescriptor && containingDeclaration is PackageFragmentDescriptor

private fun KtNameReferenceExpression.prevElementWithoutSpacesAndComments(): PsiElement? = prevLeaf {
    it.elementType !in KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET
}

private fun KtNameReferenceExpression.findSingleDescriptor(): DeclarationDescriptor? = resolveMainReferenceToDescriptors().singleOrNull()
