// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiReference
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypeAndBranch
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember

sealed class KotlinMoveRenameUsage(
    element: PsiElement,
    reference: PsiReference,
    referencedElement: PsiElement
) : MoveRenameUsageInfo(element, reference, referencedElement) {
    abstract val isInternal: Boolean

    abstract fun refresh(refExpr: KtSimpleNameExpression, referencedElement: PsiElement): UsageInfo

    sealed class Deferred(
        element: PsiElement,
        reference: PsiReference,
        referencedElement: PsiElement
    ) : KotlinMoveRenameUsage(element, reference, referencedElement) {
        abstract fun resolve(newElement: PsiElement): UsageInfo?

        class CallableReference(
            element: PsiElement,
            reference: PsiReference,
            referencedElement: PsiElement,
            val originalFile: PsiFile,
            private val addImportToOriginalFile: Boolean,
            override val isInternal: Boolean
        ) : Deferred(element, reference, referencedElement) {
            override fun refresh(refExpr: KtSimpleNameExpression, referencedElement: PsiElement): UsageInfo {
                return CallableReference(
                    refExpr,
                    refExpr.mainReference,
                    referencedElement,
                    originalFile,
                    addImportToOriginalFile,
                    isInternal
                )
            }

            override fun resolve(newElement: PsiElement): UsageInfo? {
                val target = newElement.unwrapped
                val element = element ?: return null
                val reference = reference ?: return null
                val referencedElement = referencedElement ?: return null
                if (target != null && target.isTopLevelKtOrJavaMember()) {
                    element.getStrictParentOfType<KtCallableReferenceExpression>()?.receiverExpression?.delete()
                    return Unqualifiable(
                        element,
                        reference,
                        referencedElement,
                        element.containingFile!!,
                        addImportToOriginalFile,
                        isInternal
                    )
                }
                return Qualifiable(element, reference, referencedElement, isInternal)
            }
        }
    }

    class Unqualifiable(
        element: PsiElement,
        reference: PsiReference,
        referencedElement: PsiElement,
        val originalFile: PsiFile,
        val addImportToOriginalFile: Boolean,
        override val isInternal: Boolean
    ) : KotlinMoveRenameUsage(element, reference, referencedElement) {
        override fun refresh(refExpr: KtSimpleNameExpression, referencedElement: PsiElement): UsageInfo {
            return Unqualifiable(
                refExpr,
                refExpr.mainReference,
                referencedElement,
                originalFile,
                addImportToOriginalFile,
                isInternal
            )
        }
    }

    class Qualifiable(
        element: PsiElement,
        reference: PsiReference,
        referencedElement: PsiElement,
        override val isInternal: Boolean
    ) : KotlinMoveRenameUsage(element, reference, referencedElement) {
        override fun refresh(refExpr: KtSimpleNameExpression, referencedElement: PsiElement): UsageInfo {
            return Qualifiable(refExpr, refExpr.mainReference, referencedElement, isInternal)
        }
    }

    companion object {
        fun createIfPossible(
            reference: PsiReference,
            referencedElement: PsiElement,
            addImportToOriginalFile: Boolean,
            isInternal: Boolean
        ): UsageInfo? {
            val element = reference.element

            fun createQualifiable() = Qualifiable(element, reference, referencedElement, isInternal)

            if (element !is KtSimpleNameExpression) return createQualifiable()

            if (element.getStrictParentOfType<KtSuperExpression>() != null) return null
            val containingFile = element.containingFile ?: return null

            fun createUnQualifiable() = Unqualifiable(
                element, reference, referencedElement, containingFile, addImportToOriginalFile, isInternal
            )

            fun createCallableReference() = Deferred.CallableReference(
                element, reference, referencedElement, containingFile, addImportToOriginalFile, isInternal
            )

            if (isExtensionRef(element) && reference.element.getNonStrictParentOfType<KtImportDirective>() == null) return Unqualifiable(
                element, reference, referencedElement, containingFile, addImportToOriginalFile, isInternal
            )

            element.getParentOfTypeAndBranch<KtCallableReferenceExpression> { callableReference }?.let { callable ->
                if (callable.receiverExpression != null) {
                    return if (isQualifiable(callable)) createCallableReference() else null
                }
                val target = referencedElement.unwrapped
                if (target is KtDeclaration && target.parent is KtFile) return createUnQualifiable()
                if (target is PsiMember && target.containingClass == null) return createUnQualifiable()
            }
            return createQualifiable()
        }
    }
}

