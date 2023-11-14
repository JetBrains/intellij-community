// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data

import com.intellij.diff.editor.DiffEditorTabFilesManager
import com.intellij.diff.editor.DiffVirtualFileBase
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.github.GHRegistry
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.*
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
      it ?: if (GHRegistry.isCombinedDiffEnabled()) {
        GHNewPRCombinedDiffPreviewVirtualFile(id, project, repository)
      }
      else {
        GHNewPRDiffVirtualFile(id, project, repository)
      }
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

  override fun createAndOpenDiffFile(pullRequest: GHPRIdentifier, requestFocus: Boolean) {
    diffFiles.getOrPut(pullRequest) {
      if (GHRegistry.isCombinedDiffEnabled()) {
        GHPRCombinedDiffPreviewVirtualFile(id, project, repository, pullRequest)
      }
      else {
        GHPRDiffVirtualFile(id, project, repository, pullRequest)
      }
    }.let {
      DiffEditorTabFilesManager.getInstance(project).showDiffFile(it, requestFocus)
      GHPRStatisticsCollector.logDiffOpened(project)
    }
  }

  override fun findTimelineFile(pullRequest: GHPRIdentifier): GHPRTimelineVirtualFile? = files[pullRequest]

  override fun findDiffFile(pullRequest: GHPRIdentifier): DiffVirtualFileBase? = diffFiles[pullRequest]

  override fun updateTimelineFilePresentation(details: GHPullRequestShort) {
    findTimelineFile(details.prId)?.let {
      FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(it)
    }
  }

  override fun dispose() {
    // otherwise the exception is thrown when removing an editor tab
    (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
      for (file in (files.values + diffFiles.values)) {
        FileEditorManager.getInstance(project).closeFile(file)
      }
      newPRDiffFile.get()?.also {
        FileEditorManager.getInstance(project).closeFile(it)
      }
    }
  }
}
