// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core

import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPsiUtil

fun KtExpression.dropEnclosingParenthesesIfPossible(): KtExpression {
    val innermostExpression = this
    var current = innermostExpression

    while (true) {
        val parent = current.parent as? KtParenthesizedExpression ?: break
        if (!KtPsiUtil.areParenthesesUseless(parent)) break
        current = parent
    }
    return current.replaced(innermostExpression)
}

fun String.unquote(): String = KtPsiUtil.unquoteIdentifier(this)