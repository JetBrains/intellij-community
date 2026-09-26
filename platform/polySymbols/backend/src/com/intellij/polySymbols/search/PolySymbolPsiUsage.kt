// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.search

import com.intellij.find.usages.api.PsiUsage
import com.intellij.model.Pointer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PolySymbolPsiUsage(
  override val file: PsiFile,
  override val range: TextRange,
  override val declaration: Boolean,
) : PsiUsage {

  override fun createPointer(): Pointer<PolySymbolPsiUsage> {
    val declaration = declaration
    return Pointer.fileRangePointer(file, range) { restoredFile, restoredRange ->
      PolySymbolPsiUsage(restoredFile, restoredRange, declaration)
    }
  }
}