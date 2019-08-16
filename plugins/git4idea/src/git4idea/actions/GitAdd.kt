// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Functions.identity
import com.intellij.util.PairConsumer
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.isEmpty
import com.intellij.util.containers.notNullize
import com.intellij.util.containers.stream
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitVcs
import git4idea.util.GitFileUtils
import java.util.*
import java.util.stream.Stream
import kotlin.streams.toList

class GitAdd : ScheduleForAdditionAction() {
  override fun isEnabled(e: AnActionEvent): Boolean {
    val project = e.getData(CommonDataKeys.PROJECT) ?: return false

    if (!ScheduleForAdditionAction.getUnversionedFiles(e, project).isEmpty()) return true

    val changeStream = e.getData(VcsDataKeys.CHANGES).stream()
    if (!collectPathsFromChanges(project, changeStream).isEmpty()) return true

    val filesStream = e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM).notNullize()
    return if (!collectPathsFromFiles(project, filesStream).isEmpty()) true else false

  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.getRequiredData(CommonDataKeys.PROJECT)

    val toAdd = HashSet<FilePath>()

    val changeStream = e.getData(VcsDataKeys.CHANGES).stream()
    ContainerUtil.addAll(toAdd, collectPathsFromChanges(project, changeStream).iterator())

    val filesStream = e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM).notNullize()
    ContainerUtil.addAll(toAdd, collectPathsFromFiles(project, filesStream).iterator())

    val unversionedFiles = ScheduleForAdditionAction.getUnversionedFiles(e, project).toList()

    ScheduleForAdditionAction.addUnversioned(project, unversionedFiles, e.getData(ChangesBrowserBase.DATA_KEY),
                                             if (!toAdd.isEmpty()) PairConsumer<ProgressIndicator, MutableList<VcsException>> { _, exceptions ->
                                               addPathsToVcs(project, toAdd, exceptions)
                                             }
                                             else null)
  }

  private fun addPathsToVcs(project: Project, toAdd: Collection<FilePath>, exceptions: MutableList<VcsException>) {
    VcsUtil.groupByRoots(project, toAdd, identity()).forEach { (vcsRoot, paths) ->
      try {
        if (vcsRoot.vcs !is GitVcs) return

        GitFileUtils.addPaths(project, vcsRoot.path, paths)
        VcsFileUtil.markFilesDirty(project, paths)
      }
      catch (ex: VcsException) {
        exceptions.add(ex)
      }
    }
  }

  private fun collectPathsFromChanges(project: Project, allChanges: Stream<out Change>): Stream<FilePath> {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)

    return allChanges
      .filter { change ->
        val filePath = ChangesUtil.getFilePath(change)
        vcsManager.getVcsFor(filePath) is GitVcs && isStatusForAddition(change.fileStatus)
      }
      .map { ChangesUtil.getFilePath(it) }
  }

  private fun collectPathsFromFiles(project: Project, allFiles: Stream<out VirtualFile>): Stream<FilePath> {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val changeListManager = ChangeListManager.getInstance(project)

    return allFiles
      .filter { file ->
        vcsManager.getVcsFor(file) is GitVcs && (file.isDirectory || isStatusForAddition(changeListManager.getStatus(file)))
      }
      .map{ VcsUtil.getFilePath(it) }
  }

  private fun isStatusForAddition(status: FileStatus): Boolean {
    return status === FileStatus.MODIFIED ||
           status === FileStatus.MERGED_WITH_CONFLICTS ||
           status === FileStatus.ADDED ||
           status === FileStatus.DELETED
  }
}
