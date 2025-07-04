// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile

/**
 * Cells in Kotlin Notebooks are Kotlin Scripts injected into the main (.ipynb) notebook file,
 * with a special file name suffix (.jupyter.kts).
 * A more robust approach would be to have an extension point for this kind of test.
 */
val PsiElement.isKotlinNotebookCell: Boolean get() {
    if (this !is KtFile || !isScript()) return false

    val myVirtualFile = virtualFile
    return myVirtualFile is VirtualFileWindow && myVirtualFile.name.endsWith(".jupyter.kts")
}
