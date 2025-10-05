// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors.commands

import com.intellij.codeInsight.completion.command.commands.AbstractOptimizeImportCompletionCommandProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportList

internal class KotlinOptimizeImportCompletionCommandProvider : AbstractOptimizeImportCompletionCommandProvider() {
    override fun isImportList(psiFile: PsiFile, offset: Int): Boolean {
        if (offset - 1 < 0) return false
        val element = psiFile.findElementAt(offset - 1)
        return element?.parentOfType<KtImportList>(withSelf = true) != null
    }

    override fun getTextRangeImportList(psiFile: PsiFile, offset: Int): TextRange? {
        if (psiFile is KtFile) return psiFile.importList?.textRange
        return null
    }
}