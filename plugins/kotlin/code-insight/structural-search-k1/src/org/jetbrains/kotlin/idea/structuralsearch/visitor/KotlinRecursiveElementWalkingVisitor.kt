// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.visitor

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveVisitor
import com.intellij.psi.PsiWalkingState
import org.jetbrains.kotlin.psi.KtReferenceExpression

abstract class KotlinRecursiveElementWalkingVisitor : SSRKtVisitor(), PsiRecursiveVisitor {
    private val myWalkingState: PsiWalkingState = object : PsiWalkingState(this) {
        override fun elementFinished(element: PsiElement) {}
    }

    override fun visitElement(element: PsiElement) {
        myWalkingState.elementStarted(element)
    }

    override fun visitReferenceExpression(expression: KtReferenceExpression) {
        visitExpression(expression)
        myWalkingState.startedWalking()
    }
}