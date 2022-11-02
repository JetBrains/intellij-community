// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.CodeStyleManager

fun PsiFile.adjustLineIndent(startOffset: Int, endOffset: Int) {
    if (!commitAndUnblockDocument()) return
    CodeStyleManager.getInstance(project).adjustLineIndent(this, TextRange(startOffset, endOffset))
}

/**
 * Returns a [Map] with its [SmartPsiElementPointer] keys dereferenced. Entries whose pointers cannot be dereferenced are ignored.
 */
fun <E : PsiElement, V> Map<SmartPsiElementPointer<E>, V>.dereferenceValidKeys(): Map<E, V> =
    buildMap {
        this@dereferenceValidKeys.forEach { (pointer, value) ->
          pointer.element?.let { put(it, value) }
        }
    }
