// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.startOffset

fun PsiElement.textRangeIn(other: PsiElement): TextRange = textRange.shiftLeft(other.startOffset)

fun TextRange.relativeTo(element: PsiElement): TextRange = shiftLeft(element.startOffset)