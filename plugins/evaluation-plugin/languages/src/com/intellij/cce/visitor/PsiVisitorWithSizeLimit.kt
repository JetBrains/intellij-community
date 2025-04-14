package com.intellij.cce.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

abstract class PsiVisitorWithSizeLimit(val maxSize: Int = 15000) {
  private var fileSize: Int? = null
  private val visitorHelper = LineCompletionVisitorHelper()

  fun processFileLineSizeIfNotProcessed(element: PsiElement) {
    if (fileSize == null) {
      fileSize = element.containingFile.text.length
    }
  }

  fun isFileSizeWithinLimit(): Boolean = fileSize != null && fileSize!! <= maxSize

  abstract fun isProcessableElement(element: PsiElement): Boolean

  fun shouldProcessElement(element: PsiElement): Boolean =
    isFileSizeWithinLimit() && isProcessableElement(element)

  fun getFile(): CodeFragment = visitorHelper.getFile()

  fun handleVisitElement(element: PsiElement) {
    if (shouldProcessElement(element)) {
      visitorHelper.addElement(element.node)
    }
  }

  fun handleVisitFile(file: PsiFile) {
    processFileLineSizeIfNotProcessed(file)
    visitorHelper.visitFile(file)
  }
}