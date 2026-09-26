// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.hasActualModifier
import org.jetbrains.kotlin.psi.psiUtil.isObjectLiteral

const val JVM_FIELD_CLASS_ID: String = "kotlin/jvm/JvmField"
const val JVM_STATIC_CLASS_ID: String = "kotlin/jvm/JvmStatic"

fun KtProperty.getJvmAnnotations(): List<KtAnnotationEntry> {
    return listOfNotNull(
        findAnnotation(ClassId.fromString(JVM_FIELD_CLASS_ID)),
        findAnnotation(ClassId.fromString(JVM_STATIC_CLASS_ID))
    )
}

fun KtProperty.checkMayBeConstantByFields(): Boolean {
    if (isLocal || isVar || getter != null ||
        hasModifier(KtTokens.CONST_KEYWORD) || hasModifier(KtTokens.OVERRIDE_KEYWORD) || hasActualModifier() ||
        hasDelegate() || receiverTypeReference != null
    ) {
        return false
    }
    val containingClassOrObject = this.containingClassOrObject
    if (!isTopLevel && containingClassOrObject !is KtObjectDeclaration) return false
    return containingClassOrObject?.isObjectLiteral() != true
}
