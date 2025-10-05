// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.commit

import com.intellij.dvcs.push.PushInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.JBScrollPane
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.rebase.GitInteractiveRebaseService
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel

internal class IntelliJMonorepoPrePushHandler : IssueIDPrePushHandler() {
  override val paths: List<String> = listOf(
    "/community/platform/",
    "remote-dev", // ij platform
    "plugins/kotlin/" // kotlin plugin
  )
  override val pathsToIgnore: List<String> = super.pathsToIgnore.toMutableList()
    .apply { add("/fleet/plugins/kotlin/") }
    .apply { add("/plugins/kotlin/jupyter/") }

  override val commitMessageRegex = Regex("""(?:^|.*[^-A-Z0-9])[A-Z]+-\d+.*""", RegexOption.DOT_MATCHES_ALL)
  override val ignorePattern = Regex(
    pattern = """^(?:\[.+] ?)?\[?(?:tests?|clean ?up|docs?|typo|refactor(?:ing)?|format|style|testFramework|test framework)]?.*\s.*[A-Z0-9](?:.|\n)*|(?:.*[^a-z])?WIP(?:[^a-z](?:.|\n)*)?""",
    option = RegexOption.IGNORE_CASE
  )
  override val validateCommitsOnlyFromCurrentUser: Boolean = true

  override fun isAvailable(): Boolean = Registry.`is`("intellij.monorepo.commit.message.validation.enabled", true)
  override fun getPresentableName(): @Nls String = DevKitGitBundle.message("push.commit.intellij.platform.handler.name")

  override fun handleCommitsValidationFailure(
    project: Project,
    info: PushInfo,
    commitsToWarnAbout: List<VcsFullCommitDetails>,
    modalityState: ModalityState,
  ): Boolean {
    val commitsInfo = commitsToWarnAbout.toHtml()

    val result = invokeAndWait(modalityState) {
      val dialog = CommitValidationDialog(project, commitsInfo)
      dialog.show()
      dialog.exitCode
    }

    if (result == CommitValidationDialog.EDIT_EXIT_CODE) {
      val repository = info.repository as? GitRepository ?: run {
        thisLogger().error("Unexpected repository type: ${info.repository}")
        return false
      }
      project.service<GitInteractiveRebaseService>().launchRebase(repository, commitsToWarnAbout.first())
    }

    return result == DialogWrapper.OK_EXIT_CODE
  }
}

private class CommitValidationDialog(
  project: Project,
  private val commitsInfo: String
) : DialogWrapper(project) {

  companion object {
    const val EDIT_EXIT_CODE = NEXT_USER_EXIT_CODE + 1
  }

  private val editAction = object : DialogWrapperAction(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.edit")) {
    override fun doAction(e: java.awt.event.ActionEvent) {
      close(EDIT_EXIT_CODE)
    }
  }

  private val commitAction = object : DialogWrapperAction(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.commit")) {
    override fun doAction(e: java.awt.event.ActionEvent) {
      close(OK_EXIT_CODE)
    }
  }

  init {
    title = DevKitGitBundle.message("push.commit.intellij.platform.handler.title")
    isResizable = true
    init()
    rootPane.defaultButton = getButton(editAction)
  }

  override fun createCenterPanel(): JComponent {
    val panel = JPanel(BorderLayout())
    val explanationPane = JEditorPane("text/html", DevKitGitBundle.message("push.commit.intellij.platform.message.lacks.issue.reference.body", commitsInfo)).apply {
      isEditable = false
      background = panel.background
      putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
      border = null
    }
    @Suppress("HardCodedStringLiteral")
    val commitInfoPane = JEditorPane("text/html", "<pre>${commitsInfo.replace("\n", "<br/>")}</pre>").apply {
      isEditable = false
      background = panel.background
    }
    val scrollPane = JBScrollPane(commitInfoPane).apply {
      minimumSize = Dimension(400, 150)
      preferredSize = Dimension(600, 300)
      verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
      horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
      border = null
    }
    panel.add(explanationPane, BorderLayout.NORTH)
    panel.add(scrollPane, BorderLayout.CENTER)
    return panel
  }

  override fun createActions(): Array<Action> {
    return arrayOf(
      commitAction,
      editAction,
      cancelAction
    )
  }

  override fun getDimensionServiceKey(): String {
    return "IntelliJPlatformCommitValidationDialog"
  }
}