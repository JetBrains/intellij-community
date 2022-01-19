// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.commit

import com.intellij.dvcs.push.PrePushHandler
import com.intellij.dvcs.push.PushInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.systemIndependentPath
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.history.GitHistoryUtils
import git4idea.push.GitPushSource
import git4idea.push.GitPushTarget
import git4idea.repo.GitRepositoryImpl
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.util.PsiUtil
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.extension

class KotlinPluginPrePushHandler(private val project: Project) : PrePushHandler {

  companion object {
    private const val HANDLER_ENABLED_KEY = "kotlin.commit.message.validation.enabled"
    private const val KOTLIN_PLUGIN_PATH = "plugins/kotlin/"
    private val pathsToIgnore = setOf("/test/", "/testData/", "/fleet/plugins/kotlin/")
    private val fileExtensionsNotToTrack = setOf("iml", "md")
    private val commitMessageRegex = Regex(".*KTIJ-\\d+.*", RegexOption.DOT_MATCHES_ALL /* line breaks matter */)

    internal fun containKotlinPluginSources(files: Collection<VirtualFile>): Boolean {
      return files.asSequence()
        .map { file -> Path.of(file.path) }
        .any { path ->
          val siPath = path.systemIndependentPath
          path.extension !in fileExtensionsNotToTrack
          && siPath.contains(KOTLIN_PLUGIN_PATH)
          && pathsToIgnore.none { siPath.contains(it) }
        }
    }

    internal fun commitMessageIsCorrect(message: String): Boolean = message.matches(commitMessageRegex)


    private fun <T> invokeAndWait(modalityState: ModalityState, computable: () -> T): T {
      val ref = AtomicReference<T>()
      ApplicationManager.getApplication().invokeAndWait({ ref.set(computable.invoke()) }, modalityState)
      return ref.get()
    }
  }

  override fun getPresentableName(): String =
    DevKitBundle.message("push.commit.handler.name")

  override fun handle(pushDetails: MutableList<PushInfo>, indicator: ProgressIndicator): PrePushHandler.Result {
    if (!commitsShouldBeChecked())
      return PrePushHandler.Result.OK

    val atLeastOneHasCommitsToEdit = pushDetails.any { hasCommitsToEdit(it, indicator.modalityState) }

    if (atLeastOneHasCommitsToEdit)
      return PrePushHandler.Result.ABORT_AND_CLOSE

    return PrePushHandler.Result.OK
  }

  private fun hasCommitsToEdit(pushInfo: PushInfo, modalityState: ModalityState): Boolean {
    val pushSpec = pushInfo.pushSpec
    val sourceBranchName = (pushSpec.source as GitPushSource).branch.name
    val remoteName = (pushSpec.target as GitPushTarget).branch.remote.name
    val gitRepository = pushInfo.repository as GitRepositoryImpl

    // We don't want bother people reminding them of commits which are already on remote. Being there means being on the safe side.
    // This is the case when, for example, one rebased his dev branch on recent master and has now to push not only his/her commits but
    // those that came from master as well.

    val commitHashesOfInterest =
      GitHistoryUtils.history(project, gitRepository.root, sourceBranchName, "--not", "--remotes=$remoteName", "--max-count=1000")
        .map { it.id.toShortString() }.toSet()

    val commitsToPush = pushInfo.commits // is a subset of those of interest (user decides what to push)

    val commitsToWarnAbout = commitsToPush.asSequence()
      .filter { it.id.toShortString() in commitHashesOfInterest }
      .filter(::breaksKotlinPluginMessageRules)
      .map { it.id.toShortString() to it.subject }
      .toList()

    if (commitsToWarnAbout.isEmpty())
      return false

    val commitsInfo = commitsToWarnAbout.joinToString("<br/>") { hashAndSubject ->
      "${hashAndSubject.first}: ${hashAndSubject.second}"
    }

    val commitAsIs = invokeAndWait(modalityState) {
      @Suppress("DialogTitleCapitalization")
      MessageDialogBuilder.yesNo(
        DevKitBundle.message("push.commit.message.lacks.issue.reference.title"),
        DevKitBundle.message("push.commit.message.lacks.issue.reference.body", commitsInfo)
      )
        .yesText(DevKitBundle.message("push.commit.message.lacks.issue.reference.commit"))
        .noText(DevKitBundle.message("push.commit.message.lacks.issue.reference.edit"))
        .asWarning()
        .ask(project = null)
    }

    if (commitAsIs)
      return false

    return true
  }

  private fun commitsShouldBeChecked(): Boolean =
    Registry.`is`(HANDLER_ENABLED_KEY, true) && PsiUtil.isIdeaProject(project)

  private fun breaksKotlinPluginMessageRules(commit: VcsFullCommitDetails): Boolean {
    val changedFiles = commit.changes.mapNotNull { it.virtualFile }
    return containKotlinPluginSources(changedFiles) && !commitMessageIsCorrect(commit.fullMessage)
  }
}