// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.diff.editor.DiffVirtualFileBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.*
import java.util.*

internal class GHPRFilesManagerImpl(private val project: Project,
                                    private val repository: GHRepositoryCoordinates) : GHPRFilesManager {

  // current time should be enough to distinguish the manager between launches
  private val id = System.currentTimeMillis().toString()

  private val filesEventDispatcher = EventDispatcher.create(FileListener::class.java)

  private val files = ContainerUtil.createWeakValueMap<GHPRIdentifier, GHPRTimelineVirtualFile>()
  private val diffFiles = ContainerUtil.createWeakValueMap<GHPRIdentifier, DiffVirtualFileBase>()
  private var newPRDiffFiles = ContainerUtil.createWeakValueMap<String, DiffVirtualFileBase>()

  override fun createOrGetNewPRDiffFile(sourceId: String, combinedDiff: Boolean): DiffVirtualFileBase {
    return newPRDiffFiles.getOrPut(sourceId) {
      if (combinedDiff) {
        GHNewPRCombinedDiffPreviewVirtualFile(sourceId, id, project, repository)
      }
      else {
        GHNewPRDiffVirtualFile(id, project, repository)
      }
    }
  }

  override fun createOrGetDiffFile(pullRequest: GHPRIdentifier, sourceId: String, combinedDiff: Boolean): DiffVirtualFileBase {
    return diffFiles.getOrPut(SimpleGHPRIdentifier(pullRequest)) {
      if (combinedDiff) {
        GHPRCombinedDiffPreviewVirtualFile(sourceId, id, project, repository, pullRequest)
      }
      else {
        GHPRDiffVirtualFile(id, project, repository, pullRequest)
      }
    }
  }

  override fun createAndOpenTimelineFile(pullRequest: GHPRIdentifier, requestFocus: Boolean) {
    files.getOrPut(SimpleGHPRIdentifier(pullRequest)) {
      GHPRTimelineVirtualFile(id, project, repository, pullRequest)
    }.let {
      filesEventDispatcher.multicaster.onBeforeFileOpened(it)
      FileEditorManager.getInstance(project).openFile(it, requestFocus)
      GHPRStatisticsCollector.logTimelineOpened(project)
    }
  }

  override fun createAndOpenDiffFile(pullRequest: GHPRIdentifier, requestFocus: Boolean) {
    diffFiles.getOrPut(SimpleGHPRIdentifier(pullRequest)) {
      GHPRDiffVirtualFile(id, project, repository, pullRequest)
    }.let {
      DiffEditorTabFilesManager.getInstance(project).showDiffFile(it, requestFocus)
      GHPRStatisticsCollector.logDiffOpened(project)
    }
  }

  override fun createAndOpenDiffPreviewFile(pullRequest: GHPRIdentifier, sourceId: String, requestFocus: Boolean): DiffVirtualFileBase {
    return diffFiles.getOrPut(SimpleGHPRIdentifier(pullRequest)) {
      GHPRCombinedDiffPreviewVirtualFile(sourceId, id, project, repository, pullRequest)
    }.also {
      DiffEditorTabFilesManager.getInstance(project).showDiffFile(it, requestFocus)
      GHPRStatisticsCollector.logDiffOpened(project)
    }
  }

  override fun createAndOpenNewPRDiffPreviewFile(sourceId: String, combinedDiff: Boolean, requestFocus: Boolean): DiffVirtualFileBase {
    return newPRDiffFiles.getOrPut(sourceId) {
      if (combinedDiff) {
        GHNewPRCombinedDiffPreviewVirtualFile(sourceId, id, project, repository)
      }
      else {
        GHNewPRDiffVirtualFile(id, project, repository)
      }
    }.also {
      DiffEditorTabFilesManager.getInstance(project).showDiffFile(it, requestFocus)
      GHPRStatisticsCollector.logDiffOpened(project)
    }
  }

  override fun findTimelineFile(pullRequest: GHPRIdentifier): GHPRTimelineVirtualFile? = files[SimpleGHPRIdentifier(pullRequest)]

  override fun findDiffFile(pullRequest: GHPRIdentifier): DiffVirtualFileBase? = diffFiles[SimpleGHPRIdentifier(pullRequest)]

  override fun updateTimelineFilePresentation(details: GHPullRequestShort) {
    val file = findTimelineFile(details)
    if (file != null) {
      file.details = details
      FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(file)
    }
  }

  override fun addBeforeTimelineFileOpenedListener(disposable: Disposable, listener: (file: GHPRTimelineVirtualFile) -> Unit) {
    filesEventDispatcher.addListener(object : FileListener {
      override fun onBeforeFileOpened(file: GHPRTimelineVirtualFile) = listener(file)
    }, disposable)
  }

  override fun dispose() {
    for (file in (files.values + diffFiles.values + newPRDiffFiles.values)) {
      FileEditorManager.getInstance(project).closeFile(file)
      file.isValid = false
    }
  }

  private interface FileListener : EventListener {
    fun onBeforeFileOpened(file: GHPRTimelineVirtualFile)
  }
}
