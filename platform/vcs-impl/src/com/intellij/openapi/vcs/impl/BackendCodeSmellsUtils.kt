// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.codeInsight.CodeSmellInfo
import com.intellij.ide.vfs.rpcId
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.rpc.topics.broadcast
import com.intellij.platform.vcs.impl.shared.CODE_SMELL_REMOTE_TOPIC
import com.intellij.platform.vcs.impl.shared.CodeSmellDto
import com.intellij.platform.vcs.impl.shared.ShowCodeSmellRequest

private val LOG = logger<CodeSmellDetectorImpl>()

internal fun showCodeSmellErrorsInFrontend(smellList: List<@JvmWildcard CodeSmellInfo>, project: Project){
  val dtos = convertToDtos(smellList)
  if (dtos.isEmpty()) {
    LOG.debug("the list of code smells is empty, nothing will be displayed")
    return
  }

  val showCodeSmellRequest = ShowCodeSmellRequest(dtos)
  CODE_SMELL_REMOTE_TOPIC.broadcast(project, showCodeSmellRequest)
}


private fun convertToDtos(smellList: List<CodeSmellInfo>): List<CodeSmellDto> {
  return smellList
    .sortedBy { it.textRange.startOffset }
    .mapNotNull { smellInfo ->
      val file = FileDocumentManager.getInstance().getFile(smellInfo.document) ?: return@mapNotNull null
      val filePathFromUserHome = FileUtil.getLocationRelativeToUserHome(file.presentableUrl)
      val severityName = if (smellInfo.severity == HighlightSeverity.ERROR) "ERROR" else "WARNING"

      CodeSmellDto(
        description = smellInfo.description,
        filePath = filePathFromUserHome,
        fileId = file.rpcId(),
        line = smellInfo.startLine,
        column = smellInfo.startColumn,
        severityName = severityName,
        severityValue = smellInfo.severity.myVal
      )
    }
}

