// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.imports

import com.intellij.lang.imports.ImportBlockRangeProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile

class KotlinImportBlockRangeProvider : ImportBlockRangeProvider {
    override fun getImportBlockRange(file: PsiFile): TextRange? {
        if (file !is KtFile) return null
        return file.importList?.textRange
    }
}