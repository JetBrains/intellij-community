package com.intellij.webSymbols.search

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager

class WebSymbolPsiUsage(override val file: PsiFile,
                        override val range: TextRange,
                        override val declaration: Boolean) : PsiUsage {

  override fun createPointer(): Pointer<WebSymbolPsiUsage> {
    val pointer = SmartPointerManager.getInstance(file.project).createSmartPsiFileRangePointer(file, range)
    return Pointer {
      val file: PsiFile = pointer.element ?: return@Pointer null
      val range: TextRange = pointer.range?.let(TextRange::create) ?: return@Pointer null
      WebSymbolPsiUsage(file, range, declaration)
    }
  }
}