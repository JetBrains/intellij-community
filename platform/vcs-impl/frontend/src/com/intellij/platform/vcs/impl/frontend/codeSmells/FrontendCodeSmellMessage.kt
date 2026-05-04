// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.codeSmells

import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel.Companion.createExportPrefix
import com.intellij.ide.errorTreeView.NewErrorTreeViewPanel.Companion.createRendererPrefix
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.CodeSmellDto
import com.intellij.pom.Navigatable
import com.intellij.util.ui.MessageCategory

internal class FrontendCodeSmellMessage {
  val type: Int
  val text: Array<String>
  val filePath: String
  val navigatable: Navigatable
  val exportPrefix: String
  val rendererPrefix: String

  constructor(
    smell: CodeSmellDto,
    file: VirtualFile,
    project: Project
  ) {
    this.navigatable = OpenFileDescriptor(project, file, smell.line, smell.column)

    this.type = when (smell.severityName) {
      "ERROR" -> MessageCategory.ERROR
      else -> MessageCategory.WARNING
    }

    this.text = arrayOf(smell.description)
    this.filePath = smell.filePath
    this.exportPrefix = createExportPrefix(smell.line + 1)
    this.rendererPrefix = createRendererPrefix(smell.line + 1, smell.column + 1)
  }
}

internal fun convertCodeSmellDtosToMessages(codeSmells: List<CodeSmellDto>, project: Project): List<FrontendCodeSmellMessage> {
  return codeSmells.mapNotNull { smell ->
    val file = smell.fileId.virtualFile() ?: return@mapNotNull null
    FrontendCodeSmellMessage(smell, file, project)
  }
}
