// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.IgnoredBeanFactory
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction
import com.intellij.openapi.vcs.changes.ignore.actions.getSelectedFiles
import com.intellij.openapi.vcs.changes.ignore.actions.writeIgnoreFileEntries
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.i18n.GitBundle.messagePointer
import git4idea.ignore.lang.GitExcludeFileType
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import java.util.function.Supplier
import kotlin.streams.toList

abstract class DefaultGitExcludeAction(dynamicText: @NotNull Supplier<@Nls String>,
                                       dynamicDescription: @NotNull Supplier<@Nls String>)
  : DumbAwareAction(dynamicText, dynamicDescription, GitExcludeFileType.INSTANCE.icon) {

  override fun update(e: AnActionEvent) {
    val enabled = isEnabled(e)
    e.presentation.isVisible = enabled
    e.presentation.isEnabled = enabled
  }

  protected open fun isEnabled(e: AnActionEvent): Boolean {
    val project = e.getData(CommonDataKeys.PROJECT)
    return (project != null && GitUtil.getRepositories(project).isNotEmpty())
  }

}

class AddToGitExcludeAction : DefaultGitExcludeAction(
  messagePointer("git.add.to.exclude.file.action.text"),
  messagePointer("git.add.to.exclude.file.action.description")
) {
  override fun isEnabled(e: AnActionEvent): Boolean {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return false
    val selectedFiles = getSelectedFiles(e)
    val unversionedFiles = ScheduleForAdditionAction.getUnversionedFiles(e, project)
    return isEnabled(project, selectedFiles, unversionedFiles.toList())
  }

  internal fun isEnabled(project: Project, selectedFiles: List<VirtualFile>, unversionedFiles: List<VirtualFile>): Boolean {
    val changeListManager = ChangeListManager.getInstance(project)
    //ScheduleForAdditionAction.getUnversionedFiles can return already ignored directories for VCS which doesn't support directory versioning, should filter it here
    if (unversionedFiles.none { !it.isDirectory || !changeListManager.isIgnoredFile(it) }) return false

    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    return selectedFiles.any { vcsManager.getVcsFor(it)?.name == GitVcs.NAME }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val gitVcs = GitVcs.getInstance(project)
    val selectedFiles = getSelectedFiles(e)
    if (selectedFiles.isEmpty()) return

    for ((repository, filesToAdd) in GitUtil.sortFilesByRepositoryIgnoringMissing(project, selectedFiles)) {
      val gitExclude = repository.repositoryFiles.excludeFile.let { VfsUtil.findFileByIoFile(it, true) } ?: continue
      val ignores = filesToAdd.map { file -> IgnoredBeanFactory.ignoreFile(file, project) }
      writeIgnoreFileEntries(project, gitExclude, ignores, gitVcs, repository.root)
    }
  }

}

class OpenGitExcludeAction : DefaultGitExcludeAction(
  messagePointer("git.open.exclude.file.action.text"),
  messagePointer("git.open.exclude.file.action.description")
) {

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)
    val excludeToOpen = GitUtil.getRepositories(project).map { it.repositoryFiles.excludeFile }
    for (gitExclude in excludeToOpen) {
      VfsUtil.findFileByIoFile(gitExclude, true)?.let { excludeVf ->
        OpenFileDescriptor(project, excludeVf).navigate(true)
      }
    }
  }

}
