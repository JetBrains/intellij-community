package com.intellij.cce.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.Language
import com.intellij.openapi.extensions.ExtensionPointName

interface CompletionEvaluationVisitor {
  companion object {
    val EP_NAME: ExtensionPointName<CompletionEvaluationVisitor> = ExtensionPointName.create("com.intellij.cce.completionEvaluationVisitor")
  }

  val language: Language
  fun getFile(): CodeFragment
}
