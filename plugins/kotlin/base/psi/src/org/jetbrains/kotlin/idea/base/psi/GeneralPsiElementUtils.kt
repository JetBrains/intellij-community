// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.psi

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

fun PsiElement.getLineNumber(start: Boolean = true): Int {
    val document = containingFile.viewProvider.document ?: PsiDocumentManager.getInstance(project).getDocument(containingFile)
    val index = if (start) this.startOffset else this.endOffset
    if (index > (document?.textLength ?: 0)) return 0
    return document?.getLineNumber(index) ?: 0
}