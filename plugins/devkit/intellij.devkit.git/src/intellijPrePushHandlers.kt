// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.commit

import com.intellij.dvcs.push.PushInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageConstants
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.rebase.GitInteractiveRebaseService
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls

internal class IntelliJPrePushHandler : IssueIDPrePushHandler() {
  override val paths: List<String> = listOf("community", "platform")
  override val pathsToIgnore: List<String> = listOf("plugins/kotlin/")
  override val commitMessageRegex = Regex(".*[A-Z]+-\\d+.*", RegexOption.DOT_MATCHES_ALL)
  override val ignorePattern = Regex("(tests|cleanup):.*")

  override fun isAvailable(): Boolean = Registry.`is`("intellij.commit.message.validation.enabled", true)
  override fun getPresentableName(): @Nls String = DevKitGitBundle.message("push.commit.handler.idea.name")
}

internal class IntelliJPlatformPrePushHandler : IssueIDPrePushHandler() {
  override val paths: List<String> = listOf("/community/platform/", "remote-dev")
  override val pathsToIgnore: List<String> = listOf()

  override val commitMessageRegex = Regex("""(?:^|.*[^-A-Z0-9])[A-Z]+-\d+.*""", RegexOption.DOT_MATCHES_ALL)
  override val ignorePattern = Regex("""^(?:\[.+\] ?)?\[?(?:tests?|cleanup|docs?|typo|refactor(?:ing)?|format|style|testFramework|test framework)\]?.*\s.*[A-Z0-9].*""", RegexOption.IGNORE_CASE)

  override fun isAvailable(): Boolean = Registry.`is`("intellij.platform.commit.message.validation.enabled", true)
  override fun getPresentableName(): @Nls String = DevKitGitBundle.message("push.commit.intellij.platform.handler.name")

  override fun isTargetBranchProtected(project: Project, pushInfo: PushInfo): Boolean = true

  override fun handleCommitsValidationFailure(
    project: Project,
    info: PushInfo,
    commitsToWarnAbout: List<VcsFullCommitDetails>,
    modalityState: ModalityState,
  ): Boolean {
    val commitsInfo = commitsToWarnAbout.toHtml()

    val result = invokeAndWait(modalityState) {
      MessageDialogBuilder.yesNoCancel(
        DevKitGitBundle.message("push.commit.intellij.platform.handler.title"),
        DevKitGitBundle.message("push.commit.intellij.platform.message.lacks.issue.reference.body", commitsInfo)
      )
        .yesText(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.commit"))
        .noText(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.edit"))
        .asWarning()
        .show(project)
    }

    if (result == MessageConstants.NO) {
      val repository = info.repository as? GitRepository ?: run {
        thisLogger().error("Unexpected repository type: ${info.repository}")
        return false
      }
      project.service<GitInteractiveRebaseService>().launchRebase(repository, commitsToWarnAbout.first())
    }

    return result == MessageConstants.OK
  }
}