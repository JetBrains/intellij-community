// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.annotate

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionInitializer
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.hints.VcsCodeVisionProvider
import com.intellij.codeInsight.hints.codeVision.CodeVisionFusCollector
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.vcs.CacheableAnnotationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

@Service(Level.PROJECT)
internal class AnnotationsPreloader(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val updateQueue = MergingUpdateQueue.mergingUpdateQueue(
    name = "Annotations preloader queue",
    mergingTimeSpan = 1000,
    coroutineScope = coroutineScope,
  )

  init {
    project.messageBus.connect().subscribe(VCS_CONFIGURATION_CHANGED, VcsListener { refreshSelectedFiles() })
  }

  fun schedulePreloading(file: VirtualFile) {
    if (project.isDisposed) return

    updateQueue.queue(object : DisposableUpdate(project, file) {
      override fun doRun() {
        try {
          val durationMs = measureTimeMillis {
            if (!FileEditorManager.getInstance(project).isFileOpen(file)) return
            if (file.fileType.isBinary) return
            val annotationProvider = getAnnotationProvider(project, file) ?: return
            annotationProvider.populateCache(file)
          }

          coroutineScope.launch {
            readAction {
              val psiFile = if (file.isValid) PsiManager.getInstance(project).findFile(file) else null
              if (psiFile != null) {
                CodeVisionFusCollector.reportVcsAnnotationDuration(psiFile, durationMs)
              }
            }
          }

          LOG.debug { "Preloaded VCS annotations for ${file.name} in $durationMs ms" }

          runInEdt {
            if (project.isDisposed) return@runInEdt
            CodeVisionInitializer.getInstance(project).getCodeVisionHost().invalidateProvider(
              CodeVisionHost.LensInvalidateSignal(null, listOf(VcsCodeVisionProvider.id)))
          }
        }
        catch (e: VcsException) {
          LOG.info(e)
        }
      }
    })
  }

  private fun refreshSelectedFiles() {
    if (!isEnabled()) return

    val selectedFiles = FileEditorManager.getInstance(project).selectedFiles
    for (file in selectedFiles) schedulePreloading(file)
  }

  internal class AnnotationsPreloaderFileEditorManagerListener(private val project: Project) : FileEditorManagerListener {
    init {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
      if (!isEnabled()) return
      val file = event.newFile ?: return

      project.service<AnnotationsPreloader>().schedulePreloading(file)
    }
  }

  companion object {
    private val LOG = logger<AnnotationsPreloader>()

    // TODO: check cores number?
    internal fun isEnabled(): Boolean {
      if (PowerSaveMode.isEnabled()) {
        return false
      }

      val enabledInSettings = if (Registry.`is`("editor.codeVision.new")) {
        CodeVisionSettings.getInstance().isProviderEnabled(VcsCodeVisionProvider.id)
      }
      else {
        false
      }
      return enabledInSettings || AdvancedSettings.getBoolean("vcs.annotations.preload")
    }

    private fun getAnnotationProvider(project: Project, file: VirtualFile): CacheableAnnotationProvider? {
      val status = ChangeListManager.getInstance(project).getStatus(file)
      if (status == FileStatus.UNKNOWN || status == FileStatus.ADDED || status == FileStatus.IGNORED) return null

      val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file) ?: return null
      return vcs.annotationProvider as? CacheableAnnotationProvider
    }
  }
}