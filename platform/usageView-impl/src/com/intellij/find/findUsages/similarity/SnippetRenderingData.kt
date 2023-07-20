// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity

import com.intellij.lang.Language
import com.intellij.openapi.editor.LineNumberConverter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange

data class SnippetRenderingData(
  val project: Project,
  val language: Language,
  val selectionRange: TextRange,
  val text: String,
  val converter: LineNumberConverter,
)
