// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.idea.codeinsight.utils.getFunctionLiteralByImplicitLambdaParameter
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class FirKotlinTargetElementEvaluator : KotlinTargetElementEvaluator() {
    override fun getGotoDeclarationTarget(
        element: PsiElement,
        navElement: PsiElement?
    ): PsiElement? {
        if (element is KtLightClassForFacade && navElement is KtFile && navElement.isCompiled && !element.multiFileClass) {
            val firstNavElement = navElement.declarations.firstOrNull()?.navigationElement
            if (firstNavElement != null) {
                return firstNavElement.containingFile
            }
        }

        return super.getGotoDeclarationTarget(element, navElement)
    }

    override fun findLambdaOpenLBraceForGeneratedIt(ref: PsiReference): PsiElement? {
        return (ref.element as? KtNameReferenceExpression)?.getFunctionLiteralByImplicitLambdaParameter()?.lBrace?.nextSibling
    }

    override fun findReceiverForThisInExtensionFunction(ref: PsiReference): PsiElement? {
        // TODO: implement
        return null
    }
}
