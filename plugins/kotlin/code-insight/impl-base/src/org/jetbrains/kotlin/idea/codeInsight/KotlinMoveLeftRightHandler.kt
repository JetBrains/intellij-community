// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.editorActions.moveLeftRight.MoveElementLeftRightHandler
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.*

class KotlinMoveLeftRightHandler : MoveElementLeftRightHandler() {
    override fun getMovableSubElements(element: PsiElement): Array<PsiElement> {
        when (element) {
            is KtParameterList -> return element.parameters.toTypedArray()
            is KtValueArgumentList -> return element.arguments.toTypedArray()
            is KtArrayAccessExpression -> return element.indexExpressions.toTypedArray()
            is KtTypeParameterList -> return element.parameters.toTypedArray()
            is KtSuperTypeList -> return element.entries.toTypedArray()
            is KtTypeConstraintList -> return element.constraints.toTypedArray()
            //TODO
//            is KtClass -> if (element.isEnum()) return element.declarations.filterIsInstance<KtEnumEntry>().toTypedArray()
        }

        return emptyArray()
    }
}
