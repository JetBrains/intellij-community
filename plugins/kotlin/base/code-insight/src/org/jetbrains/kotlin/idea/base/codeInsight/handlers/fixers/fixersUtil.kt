// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.base.codeInsight.handlers.fixers

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

val PsiElement.range: TextRange get() = textRange!!
val TextRange.start: Int get() = startOffset
val TextRange.end: Int get() = endOffset

fun PsiElement.startLine(doc: Document): Int = doc.getLineNumber(range.start)
fun PsiElement.endLine(doc: Document): Int = doc.getLineNumber(range.end)
fun PsiElement?.isWithCaret(caret: Int) = this?.textRange?.contains(caret) == true
