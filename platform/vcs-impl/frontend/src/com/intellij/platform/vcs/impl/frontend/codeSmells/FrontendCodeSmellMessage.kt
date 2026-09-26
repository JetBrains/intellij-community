// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.codeSmells

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel.Companion.createExportPrefix
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel.Companion.createRendererPrefix
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.platform.vcs.impl.shared.CodeSmellDto
import com.intellij.pom.Navigatable
import com.intellij.util.ui.MessageCategory

internal class FrontendCodeSmellMessage(
  val type: Int,
  val text: Array<String>,
  val filePath: String,
  val navigatable: Navigatable,
  val exportPrefix: String,
  val rendererPrefix: String
)

internal fun convertCodeSmellDtosToMessages(codeSmells: List<CodeSmellDto>, project: Project): List<FrontendCodeSmellMessage> {
  return codeSmells.mapNotNull { smell ->
    val file = smell.fileId.virtualFile() ?: return@mapNotNull null

    val type = when (smell.severityName) {
      "ERROR" -> MessageCategory.ERROR
      else -> MessageCategory.WARNING
    }

    // the logic behind the prefix creation is taken from the original (monolithic) implementation in
    // com.intellij.openapi.vcs.impl.CodeSmellDetectorImpl.showCodeSmellErrors
    val exportPrefix = createExportPrefix(smell.line + 1)
    val rendererPrefix = createRendererPrefix(smell.line + 1, smell.column + 1)

    FrontendCodeSmellMessage(
      navigatable = OpenFileDescriptor(project, file, smell.line, smell.column),
      type = type,
      text = arrayOf(smell.description),
      filePath = smell.filePath,
      exportPrefix = exportPrefix,
      rendererPrefix = rendererPrefix
    )
  }
}
