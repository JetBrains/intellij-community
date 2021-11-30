// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.commit

import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.util.PairConsumer
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.PsiUtil
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

class KotlinPluginCommitMessageHandlerFactory : CheckinHandlerFactory() {

  companion object {
    private const val kotlinPluginPath = "plugins/kotlin/"
    private val pathsToIgnore = setOf("/test/", "/testData/")
    private val fileExtensionsNotToTrack = setOf("iml", "md")
    private val commitMessageRegex = Regex(".*KTIJ-\\d+.*")
  }

  override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler =
    YouTrackIssueCommitMessageHandler(panel)


  private class YouTrackIssueCommitMessageHandler(private val checkinPanel: CheckinProjectPanel) : CheckinHandler() {

    companion object {
      const val HANDLER_ENABLED_KEY = "kotlin.commit.message.validation.enabled"
    }

    private val project = checkinPanel.project

    // IMPORTANT!!! Method is called only once in case of non-modal commit window.
    // Literally, we cannot give the user smart-appearing check-box "Check my message".
    // Other than that we cannot track files selection. If relevant file gets selected we won't react.
    override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent? = null

    // IMPORTANT: called independently of getBeforeCheckinConfigurationPanel() result, i.e. always
    override fun beforeCheckin(
      executor: CommitExecutor?,
      additionalDataConsumer: PairConsumer<Any, Any>?
    ): ReturnResult {

      val messageHandlerEnabled = Registry.`is`(HANDLER_ENABLED_KEY, true)

      if (!messageHandlerEnabled
        || !commitMessageShouldBeChecked()
        || checkinPanel.commitMessage.matches(commitMessageRegex)
      ) {
        return ReturnResult.COMMIT // as is
      }

      val commitAsIs = MessageDialogBuilder.yesNo(
        DevKitBundle.message("commit.message.lacks.issue.reference.title"),
        DevKitBundle.message("commit.message.lacks.issue.reference.body")
      )
        .yesText(DevKitBundle.message("commit.message.lacks.issue.reference.commit"))
        .noText(DevKitBundle.message("commit.message.lacks.issue.reference.edit"))
        .asWarning()
        .ask(project)

      if (commitAsIs)
        return ReturnResult.COMMIT

      return ReturnResult.CANCEL
    }

    private fun commitMessageShouldBeChecked(): Boolean =
      PsiUtil.isIdeaProject(project) && selectedFilesBelongToKotlinIdePlugin()

    private fun selectedFilesBelongToKotlinIdePlugin(): Boolean {
      return checkinPanel.files.asSequence()
        .map { file -> file.toPath() }
        .any { path ->
          val siPath = path.systemIndependentPath
          path.isRegularFile()
            && path.extension !in fileExtensionsNotToTrack
            && siPath.contains(kotlinPluginPath)
            && pathsToIgnore.none { siPath.contains(it) }
        }
    }
  }
}