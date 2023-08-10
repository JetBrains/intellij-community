// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
