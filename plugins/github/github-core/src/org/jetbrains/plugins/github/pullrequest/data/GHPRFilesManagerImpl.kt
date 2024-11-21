// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.collaboration.util.CodeReviewFilesUtil
import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.diff.editor.DiffVirtualFileBase
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.addIfNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.GHNewPRDiffVirtualFile
import org.jetbrains.plugins.github.pullrequest.GHPRDiffVirtualFile
import org.jetbrains.plugins.github.pullrequest.GHPRStatisticsCollector
import org.jetbrains.plugins.github.pullrequest.GHPRTimelineVirtualFile
import java.util.concurrent.atomic.AtomicReference

internal class GHPRFilesManagerImpl(private val project: Project,
                                    private val repository: GHRepositoryCoordinates) : GHPRFilesManager {

  // current time should be enough to distinguish the manager between launches
  override val id: String = System.currentTimeMillis().toString()

  private val files = ContainerUtil.createWeakValueMap<GHPRIdentifier, GHPRTimelineVirtualFile>()
  private val diffFiles = ContainerUtil.createWeakValueMap<GHPRIdentifier, DiffVirtualFileBase>()
  private var newPRDiffFile: AtomicReference<DiffVirtualFileBase?> = AtomicReference()

  override fun createOrGetNewPRDiffFile(): DiffVirtualFileBase {
    return newPRDiffFile.updateAndGet {
      it ?: GHNewPRDiffVirtualFile(id, project, repository)
    }!!
  }

  override fun createAndOpenTimelineFile(pullRequest: GHPRIdentifier, requestFocus: Boolean) {
    files.getOrPut(pullRequest) {
      GHPRTimelineVirtualFile(id, project, repository, pullRequest)
    }.let {
      FileEditorManager.getInstance(project).openFile(it, requestFocus)
      GHPRStatisticsCollector.logTimelineOpened(project)
    }
  }

  override fun createAndOpenDiffFile(pullRequest: GHPRIdentifier?, requestFocus: Boolean) {
    if (pullRequest == null) {
      createOrGetNewPRDiffFile()
    }
    else {
      diffFiles.getOrPut(pullRequest) {
        GHPRDiffVirtualFile(id, project, repository, pullRequest)
      }.also {
        GHPRStatisticsCollector.logDiffOpened(project)
      }
    }.let {
      DiffEditorTabFilesManager.getInstance(project).showDiffFile(it, requestFocus)
    }
  }


  override fun findTimelineFile(pullRequest: GHPRIdentifier): GHPRTimelineVirtualFile? = files[pullRequest]

  override fun findDiffFile(pullRequest: GHPRIdentifier): DiffVirtualFileBase? = diffFiles[pullRequest]

  override fun updateTimelineFilePresentation(details: GHPullRequestShort) {
    findTimelineFile(details.prId)?.let {
      FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(it)
    }
  }

  override suspend fun closeNewPrFile() {
    val file = newPRDiffFile.get() ?: return
    withContext(Dispatchers.EDT) {
      val fileManager = project.serviceAsync<FileEditorManager>()
      writeAction {
        CodeReviewFilesUtil.closeFilesSafely(fileManager, listOf(file))
      }
    }
  }

  override fun dispose() {
    if (project.isDisposed) return
    val fileManager = FileEditorManager.getInstance(project)
    val files = buildList {
      addAll(files.values)
      addAll(diffFiles.values)
      addIfNotNull(newPRDiffFile.get())
    }
    CodeReviewFilesUtil.closeFilesSafely(fileManager, files)
  }
}
