// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.util

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiLiteral
import org.jetbrains.kotlin.load.java.JvmAnnotationNames
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtWhenExpression

fun KtExpression.resultingWhens(): List<KtWhenExpression> = when (this) {
    is KtWhenExpression -> listOf(this) + entries.map { it.expression?.resultingWhens() ?: listOf() }.flatten()
    is KtIfExpression -> (then?.resultingWhens() ?: listOf()) + (`else`?.resultingWhens() ?: listOf())
    is KtBinaryExpression -> (left?.resultingWhens() ?: listOf()) + (right?.resultingWhens() ?: listOf())
    is KtUnaryExpression -> this.baseExpression?.resultingWhens() ?: listOf()
    is KtBlockExpression -> statements.lastOrNull()?.resultingWhens() ?: listOf()
    else -> listOf()
}

fun PsiClass.isSyntheticKotlinClass(): Boolean {
    if ('$' !in name!!) return false // optimization to not analyze annotations of all classes
    val metadata = modifierList?.findAnnotation(JvmAnnotationNames.METADATA_FQ_NAME.asString())
    return (metadata?.findAttributeValue(JvmAnnotationNames.KIND_FIELD_NAME) as? PsiLiteral)?.value ==
            KotlinClassHeader.Kind.SYNTHETIC_CLASS.id
}