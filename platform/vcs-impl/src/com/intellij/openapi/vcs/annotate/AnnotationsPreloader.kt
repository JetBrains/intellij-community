// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.annotate

import com.intellij.codeInsight.hints.isCodeAuthorInlayHintsEnabled
import com.intellij.codeInsight.hints.refreshCodeAuthorInlayHints
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.vcs.CacheableAnnotationProvider

@Service(Level.PROJECT)
internal class AnnotationsPreloader(private val project: Project) {
  private val updateQueue = MergingUpdateQueue("Annotations preloader queue", 1000, true, null, project, null, false)

  fun schedulePreloading(file: VirtualFile) {
    if (project.isDisposed || file.fileType.isBinary) return

    updateQueue.queue(object : DisposableUpdate(project, file) {
      override fun doRun() {
        try {
          val start = if (LOG.isDebugEnabled) System.currentTimeMillis() else 0

          if (!FileEditorManager.getInstance(project).isFileOpen(file)) return

          val fileStatus = ChangeListManager.getInstance(project).getStatus(file)
          if (fileStatus == FileStatus.UNKNOWN || fileStatus == FileStatus.ADDED || fileStatus == FileStatus.IGNORED) return

          val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file) ?: return
          val annotationProvider = vcs.annotationProvider as? CacheableAnnotationProvider ?: return

          annotationProvider.populateCache(file)
          LOG.debug { "Preloaded VCS annotations for ${file.name} in ${System.currentTimeMillis() - start} ms" }

          refreshCodeAuthorInlayHints()
        }
        catch (e: VcsException) {
          LOG.info(e)
        }
      }
    })
  }

  internal class AnnotationsPreloaderFileEditorManagerListener(private val project: Project) : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
      if (!isEnabled()) return
      val file = event.newFile ?: return

      project.service<AnnotationsPreloader>().schedulePreloading(file)
    }
  }

  companion object {
    private val LOG = logger<AnnotationsPreloader>()

    // TODO: check cores number?
    private fun isEnabled(): Boolean =
      (isCodeAuthorInlayHintsEnabled() || AdvancedSettings.getBoolean("vcs.annotations.preload")) && !PowerSaveMode.isEnabled()
  }
}