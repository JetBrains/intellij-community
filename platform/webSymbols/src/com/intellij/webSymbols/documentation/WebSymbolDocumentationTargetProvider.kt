// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.documentation

import com.intellij.model.psi.impl.targetSymbols
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.webSymbols.WebSymbol
import kotlin.math.max

class WebSymbolDocumentationTargetProvider : DocumentationTargetProvider {
  override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
    val location = getContextElement(file, offset)
    return targetSymbols(file, offset).mapNotNull {
      (it as? WebSymbol)?.getDocumentationTarget(location)
    }
  }

  private fun getContextElement(file: PsiFile, offset: Int): PsiElement? =
    if (offset == file.textLength)
      file.findElementAt(max(0, offset - 1))
    else
      file.findElementAt(offset)
}