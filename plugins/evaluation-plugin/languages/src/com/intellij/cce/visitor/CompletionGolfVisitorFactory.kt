// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.golf.CompletionGolfMode
import com.intellij.openapi.extensions.ExtensionPointName

interface CompletionGolfVisitorFactory {
  val language: Language
  fun createVisitor(featureName: String, mode: CompletionGolfMode): CompletionGolfEvaluationVisitor

  companion object {
    val EP_NAME: ExtensionPointName<CompletionGolfVisitorFactory> = ExtensionPointName.create(
      "com.intellij.cce.completionGolfVisitorFactory"
    )
  }
}
