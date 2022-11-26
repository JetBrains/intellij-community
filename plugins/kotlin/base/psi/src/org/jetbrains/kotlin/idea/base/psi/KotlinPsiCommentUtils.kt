// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.psi

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.nextLeafs
import org.jetbrains.kotlin.psi.KtExpression

/**
 * Returns all [PsiComment]s between [this] and the next element that is neither a comment, same-line whitespace, nor allowed via [allow].
 * Whitespace between comments is kept intact. If [allowNewline] is specified, the tail comments may span multiple lines.
 */
fun KtExpression.tailComments(allowNewline: Boolean = false, allow: (PsiElement) -> Boolean): List<PsiElement> =
    this.nextLeafs
        .takeWhile { it is PsiComment || (it is PsiWhiteSpace && (allowNewline || !it.textContains('\n'))) || allow(it) }
        .dropWhile { it !is PsiComment }
        .filter { it is PsiComment || it is PsiWhiteSpace} // Filter out elements tolerated by [allow].
        .toList()
        .dropLastWhile { it !is PsiComment }
