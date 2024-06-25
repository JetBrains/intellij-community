// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.commit

import com.intellij.dvcs.push.PrePushHandler
import com.intellij.dvcs.push.PushInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.config.GitSharedSettings
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString

internal abstract class IssueIDPrePushHandler : PrePushHandler {
  abstract val paths: List<String>
  open val pathsToIgnore = listOf("/test/", "/testData/")
  abstract val commitMessageRegex: Regex
  open val ignorePattern: Regex = Regex("(?!.*)")

  abstract fun isAvailable(): Boolean

  internal fun containSources(files: Collection<VirtualFile>) =
    files.asSequence()
      .map { file -> Path.of(file.path) }
      .any { path ->
        val siPath = path.invariantSeparatorsPathString
        path.extension !in fileExtensionsNotToTrack
        && paths.any { siPath.contains(it) }
        && pathsToIgnore.none { siPath.contains(it) }
      }

  fun commitMessageIsCorrect(message: String): Boolean = message.matches(commitMessageRegex) || message.matches(ignorePattern)

  companion object {
    private val fileExtensionsNotToTrack = setOf("iml", "md")

    private fun <T> invokeAndWait(modalityState: ModalityState, computable: () -> T): T {
      val ref = AtomicReference<T>()
      ApplicationManager.getApplication().invokeAndWait({ ref.set(computable.invoke()) }, modalityState)
      return ref.get()
    }
  }

  private fun handlerIsApplicable(project: Project): Boolean = isAvailable() && IntelliJProjectUtil.isIntelliJPlatformProject(project)

  override fun handle(project: Project, pushDetails: MutableList<PushInfo>, indicator: ProgressIndicator): PrePushHandler.Result {
    if (!handlerIsApplicable(project)) return PrePushHandler.Result.OK

    return if (pushDetails.any { it.isTargetBranchProtected(project) && it.hasCommitsToEdit(indicator.modalityState) })
      PrePushHandler.Result.ABORT_AND_CLOSE
    else PrePushHandler.Result.OK
  }

  private fun PushInfo.isTargetBranchProtected(project: Project) = GitSharedSettings.getInstance(project).isBranchProtected(pushSpec.target.presentation)

  private fun PushInfo.hasCommitsToEdit(modalityState: ModalityState): Boolean {
    val commitsToWarnAbout = commits.asSequence()
      .filter(::breaksMessageRules)
      .map { it.id.toShortString() to it.subject }
      .toList()

    if (commitsToWarnAbout.isEmpty()) return false

    val commitsInfo = commitsToWarnAbout.joinToString("<br/>") { hashAndSubject ->
      "${hashAndSubject.first}: ${hashAndSubject.second}"
    }

    val commitAsIs = invokeAndWait(modalityState) {
      @Suppress("DialogTitleCapitalization")
      MessageDialogBuilder.yesNo(
        DevKitGitBundle.message("push.commit.message.lacks.issue.reference.title"),
        DevKitGitBundle.message("push.commit.message.lacks.issue.reference.body", commitsInfo)
      )
        .yesText(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.commit"))
        .noText(DevKitGitBundle.message("push.commit.message.lacks.issue.reference.edit"))
        .asWarning()
        .ask(project = null)
    }

    return !commitAsIs
  }

  private fun breaksMessageRules(commit: VcsFullCommitDetails) =
    containSources(commit.changes.mapNotNull { it.virtualFile }) && !commitMessageIsCorrect(commit.fullMessage)
}