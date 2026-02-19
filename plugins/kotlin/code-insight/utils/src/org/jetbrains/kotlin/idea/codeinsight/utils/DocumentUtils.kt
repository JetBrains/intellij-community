// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

fun PsiFile.commitAndUnblockDocument(): Boolean {
    val virtualFile = this.virtualFile ?: return false
    val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return false
    val documentManager = PsiDocumentManager.getInstance(project)
    documentManager.doPostponedOperationsAndUnblockDocument(document)
    documentManager.commitDocument(document)
    return true
}
