// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.commit

import com.intellij.dvcs.push.PushInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.log.VcsFullCommitDetails

internal class ToolboxPrePushHandler : IssueIDPrePushHandler() {
  override val paths = listOf("toolbox/", "station/")

  override val acceptableProjects: List<String> = super.acceptableProjects + listOf(
    "TBX", "IDES"
  )

  private val acceptableGroup = listOf(
    "docs", "doc",
    "cleanup", "typo",
    "refactoring", "refactor", "format",
    "tests", "test"
  )

  private val groupPattern = Regex("""\b(${acceptableGroup.joinToString("|")})\b.*""", RegexOption.IGNORE_CASE)

  override val commitMessageRegex: Regex = Regex(
    "(${buildRegexFromAcceptableProjects().pattern})|(${groupPattern.pattern})",
    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
  )
  override fun isAvailable(): Boolean = Registry.`is`("toolbox.commit.message.validation.enabled", true)

  override fun getPresentableName(): String = DevKitGitBundle.message("toolbox.commit.handler.name")

  override fun handleCommitsValidationFailure(project: Project, info: PushInfo, commitsToWarnAbout: List<VcsFullCommitDetails>, modalityState: ModalityState): Boolean {
    val commitsInfo = commitsToWarnAbout.toHtml()

    val commitAsIs = invokeAndWait(modalityState) {
      @Suppress("DialogTitleCapitalization")
      MessageDialogBuilder.yesNo(
        DevKitGitBundle.message("toolbox.push.commit.message.lacks.issue.reference.title"),
        DevKitGitBundle.message("toolbox.push.commit.message.lacks.issue.reference.body", commitsInfo)
      )
        .yesText(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.commit"))
        .noText(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.edit"))
        .asWarning()
        .ask(project)
    }

    return commitAsIs
  }
}
