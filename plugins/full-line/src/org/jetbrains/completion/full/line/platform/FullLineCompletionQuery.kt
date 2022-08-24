package org.jetbrains.completion.full.line.platform

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import org.jetbrains.completion.full.line.FullLineCompletionMode

data class FullLineCompletionQuery(
  val mode: FullLineCompletionMode,
  val context: String,
  val filename: String,
  val prefix: String,
  val offset: Int,
  val language: Language,
  val project: Project,
  val rollbackPrefix: List<String>
)
