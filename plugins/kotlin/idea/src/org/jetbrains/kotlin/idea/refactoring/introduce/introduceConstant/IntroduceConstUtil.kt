// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.refactoring.introduce.introduceConstant

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.structuralsearch.visitor.KotlinRecursiveElementVisitor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull

/**
 * [FqName] of operators which can be used in a kotlin const expression.
 */
private val constOperatorNames = listOf("Byte", "Short", "Int", "Long", "UByte", "UShort", "UInt", "ULong")
    .flatMap {
        listOf(
            FqName("kotlin.$it.equals"),
            FqName("kotlin.$it.compareTo"),
            FqName("kotlin.$it.plus"),
            FqName("kotlin.$it.minus"),
            FqName("kotlin.$it.times"),
            FqName("kotlin.$it.div"),
            FqName("kotlin.$it.rem"),
        )
    }.plus(listOf("Byte", "Short", "Int", "Long")
        .flatMap {
            listOf(
                FqName("kotlin.$it.unaryPlus"),
                FqName("kotlin.$it.unaryMinus"),
            )
        }
    ).plus(
        listOf(
            FqName("kotlin.Char.equals"),
            FqName("kotlin.Char.compareTo"),
            FqName("kotlin.Char.plus"),
            FqName("kotlin.Char.minus"),
        )
    )
    .plus(
        listOf(
            FqName("kotlin.String.equals"),
            FqName("kotlin.String.compareTo"),
            FqName("kotlin.String.plus"),
        )
    )

fun PsiElement.isNotConst() = !isConst()

private fun PsiElement.isConst(): Boolean {
    when (this) {
        is KtBinaryExpression -> {
            val visitor = ConstKotlinRecursiveElementVisitor(root = this)
            visitor.visitBinaryExpression(this)
            return visitor.isConst
        }
        is KtPrefixExpression -> {
            val visitor = ConstKotlinRecursiveElementVisitor(root = this)
            visitor.visitPrefixExpression(this)
            return visitor.isConst
        }
        else -> {
            return this is KtConstantExpression
                    || (this is KtStringTemplateExpression && !this.hasInterpolation())
                    || this is KtLiteralStringTemplateEntry
                    || (this is KtOperationReferenceExpression && constOperatorNames.contains(this.resolveToCall()?.resultingDescriptor?.fqNameOrNull()))
        }
    }
}

private class ConstKotlinRecursiveElementVisitor(val root: PsiElement) : KotlinRecursiveElementVisitor() {
    var isConst = true
    override fun visitKtElement(element: KtElement) {
        if (isConst) {
            super.visitKtElement(element)
            if (element != root) {
                isConst = isConst && element.isConst()
            }
        }
    }
}