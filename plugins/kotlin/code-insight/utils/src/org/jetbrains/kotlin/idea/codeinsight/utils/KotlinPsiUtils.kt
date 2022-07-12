// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.*

fun KtContainerNode.getControlFlowElementDescription(): String? {
    when (node.elementType) {
        KtNodeTypes.THEN -> return "if"
        KtNodeTypes.ELSE -> return "else"
        KtNodeTypes.BODY -> {
            when (parent) {
                is KtWhileExpression -> return "while"
                is KtDoWhileExpression -> return "do...while"
                is KtForExpression -> return "for"
            }
        }
    }
    return null
}

fun removeProperty(ktProperty: KtProperty) {
    val initializer = ktProperty.initializer
    if (initializer != null && initializer !is KtConstantExpression) {
        val commentSaver = CommentSaver(ktProperty)
        val replaced = ktProperty.replace(initializer)
        commentSaver.restore(replaced)
    } else {
        ktProperty.delete()
    }
}