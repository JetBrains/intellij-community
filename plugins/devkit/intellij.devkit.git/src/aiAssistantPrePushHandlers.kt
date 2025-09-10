// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.commit

import com.intellij.dvcs.push.PushInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.log.VcsFullCommitDetails

internal class AiAssistantPluginPrePushHandler : IssueIDPrePushHandler() {
  override val paths: List<String> = listOf("plugins/llm/", "plugins/llm-installer/", "plugins/full-line/")
  override val commitMessageRegex = Regex(".*\\b[A-Z]{2,}-\\d+\\b.*", RegexOption.DOT_MATCHES_ALL /* line breaks matter */)
  private val protectedBranches = listOf("ij-ai/", "ij-aia/", "ide-next/")

  override fun isTargetBranchProtected(project: Project, pushInfo: PushInfo): Boolean {
    return super.isTargetBranchProtected(project, pushInfo) || protectedBranches.any { pushInfo.pushSpec.target.presentation.startsWith(it) }
  }

  override fun isAvailable(): Boolean = Registry.`is`("aia.commit.message.validation.enabled", true)

  override fun handleCommitsValidationFailure(
    project: Project,
    info: PushInfo,
    commitsToWarnAbout: List<VcsFullCommitDetails>,
    modalityState: ModalityState,
  ): Boolean {
    val commitsInfo = commitsToWarnAbout.toHtml()

    val commitAsIs = invokeAndWait(modalityState) {
      @Suppress("DialogTitleCapitalization")
      MessageDialogBuilder.yesNo(
        DevKitGitBundle.message("aia.push.commit.message.lacks.issue.reference.title"),
        DevKitGitBundle.message("aia.push.commit.message.lacks.issue.reference.body", commitsInfo)
      )
        .yesText(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.commit"))
        .noText(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.edit"))
        .asWarning()
        .ask(project = null)
    }

    return commitAsIs
  }

  override fun getPresentableName(): String = DevKitGitBundle.message("aia.commit.handler.name")
}