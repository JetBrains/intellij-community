package com.intellij.cce.visitor

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.cce.core.Language
import com.intellij.cce.core.CodeFragment

interface CompletionEvaluationVisitor {
  companion object {
    val EP_NAME: ExtensionPointName<CompletionEvaluationVisitor> = ExtensionPointName.create("com.intellij.cce.completionEvaluationVisitor")
  }

  val language: Language
  fun getFile(): CodeFragment
}