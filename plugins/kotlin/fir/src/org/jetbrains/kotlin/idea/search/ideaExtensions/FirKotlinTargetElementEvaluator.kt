/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.search.ideaExtensions

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import org.jetbrains.kotlin.idea.codeinsight.utils.getFunctionLiteralByImplicitLambdaParameter
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class FirKotlinTargetElementEvaluator : KotlinTargetElementEvaluator() {
    override fun findLambdaOpenLBraceForGeneratedIt(ref: PsiReference): PsiElement? {
        return (ref.element as? KtNameReferenceExpression)?.getFunctionLiteralByImplicitLambdaParameter()?.lBrace?.nextSibling
    }

    override fun findReceiverForThisInExtensionFunction(ref: PsiReference): PsiElement? {
        // TODO: implement
        return null
    }
}
