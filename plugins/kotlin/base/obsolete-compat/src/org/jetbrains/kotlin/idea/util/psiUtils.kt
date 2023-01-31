// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("DeprecatedCallableAddReplaceWith")

package org.jetbrains.kotlin.idea.util

import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager

@Deprecated("Use org.jetbrains.kotlin.idea.base.util.reformatted() instead.")
fun PsiElement.reformatted(canChangeWhiteSpacesOnly: Boolean = false): PsiElement = let {
    CodeStyleManager.getInstance(it.project).reformat(it, canChangeWhiteSpacesOnly)
}