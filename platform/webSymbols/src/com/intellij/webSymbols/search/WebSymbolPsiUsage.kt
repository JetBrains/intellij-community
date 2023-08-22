// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.search

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

class WebSymbolPsiUsage(override val file: PsiFile,
                        override val range: TextRange,
                        override val declaration: Boolean) : PsiUsage {

  override fun createPointer(): Pointer<WebSymbolPsiUsage> {
    val declaration = declaration
    return Pointer.fileRangePointer(file, range) { restoredFile, restoredRange ->
      WebSymbolPsiUsage(restoredFile, restoredRange, declaration)
    }
  }
}