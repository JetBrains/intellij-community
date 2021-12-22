/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithSource
import org.jetbrains.kotlin.idea.refactoring.rename.RenameKotlinImplicitLambdaParameter.Companion.isAutoCreatedItUsage
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.source.getPsi

class FE10KotlinTargetElementEvaluator : KotlinTargetElementEvaluator() {
    companion object {
        fun findLambdaOpenLBraceForGeneratedIt(ref: PsiReference): PsiElement? {
            val element: PsiElement = ref.element
            if (element.text != "it") return null

            if (element !is KtNameReferenceExpression || !isAutoCreatedItUsage(element)) return null

            val itDescriptor = element.resolveMainReferenceToDescriptors().singleOrNull() ?: return null
            val descriptorWithSource = itDescriptor.containingDeclaration as? DeclarationDescriptorWithSource ?: return null
            val lambdaExpression = descriptorWithSource.source.getPsi()?.parent as? KtLambdaExpression ?: return null
            return lambdaExpression.leftCurlyBrace.treeNext?.psi
        }
    }

    override fun findLambdaOpenLBraceForGeneratedIt(ref: PsiReference): PsiElement? =
        Companion.findLambdaOpenLBraceForGeneratedIt(ref)

    override fun findReceiverForThisInExtensionFunction(ref: PsiReference): PsiElement? {
        val element: PsiElement = ref.element
        if (element.text != "this") return null

        if (element !is KtNameReferenceExpression) return null
        val callableDescriptor = element.resolveMainReferenceToDescriptors().singleOrNull() as? CallableDescriptor ?: return null

        if (!callableDescriptor.isExtension) return null
        val callableDeclaration = callableDescriptor.source.getPsi() as? KtCallableDeclaration ?: return null

        return callableDeclaration.receiverTypeReference
    }


}