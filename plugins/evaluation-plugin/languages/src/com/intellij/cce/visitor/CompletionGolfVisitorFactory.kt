package com.intellij.cce.visitor

import com.intellij.cce.actions.CompletionGolfMode
import com.intellij.cce.core.Language
import com.intellij.openapi.extensions.ExtensionPointName

interface CompletionGolfVisitorFactory {
  val language: Language
  fun createVisitor(mode: CompletionGolfMode): CompletionGolfEvaluationVisitor

  companion object {
    val EP_NAME: ExtensionPointName<CompletionGolfVisitorFactory> = ExtensionPointName.create(
      "com.intellij.cce.completionGolfVisitorFactory"
    )
  }
}
