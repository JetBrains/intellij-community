package com.intellij.cce.visitor

import com.intellij.cce.core.CodeFragment
import com.intellij.cce.core.Language
import com.intellij.openapi.extensions.ExtensionPointName

interface EvaluationVisitor {
  companion object {
    val EP_NAME: ExtensionPointName<EvaluationVisitor> = ExtensionPointName.create("com.intellij.cce.completionEvaluationVisitor")
  }

  val language: Language
  val feature: String

  fun getFile(): CodeFragment
}
