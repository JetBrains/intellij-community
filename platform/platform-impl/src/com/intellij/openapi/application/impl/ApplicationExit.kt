// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.resetThreadContext
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.application.Application
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.PreparedProjectCloseBatch
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.helpers.use
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

/**
 * Saves the application and the prepared projects, then disposes the projects.
 * Returns `null` on a veto, without calling either disposal callback.
 * A failure during the exit is logged and the exit goes on.
 */
@ApiStatus.Internal
@JvmOverloads
fun <T> saveAndCloseProjectsOnExit(
  application: Application,
  tracer: IJTracer,
  saveApplicationSettings: Boolean,
  checkCanClose: Boolean,
  beforeProjectClose: Runnable,
  afterProjectClose: Supplier<T>,
  prepareProjects: (checkCanClose: Boolean) -> PreparedProjectCloseBatch? = {
    val projectManager = ProjectManagerEx.getInstanceExIfCreated()
    if (projectManager == null) NoProjects else projectManager.prepareProjectsForExit(it)
  },
): T? {
  val saveHandler = SaveAndSyncHandler.getInstance()
  return resetThreadContext {
    val closed = saveHandler.disableAutoSave().use {
      val closeBatch = try {
        prepareProjects(checkCanClose)
      }
      catch (e: Throwable) {
        ApplicationImpl.logErrorDuringExit("Failed to prepare projects for closing", e)
        NoProjects
      }
      if (closeBatch == null) {
        return@use false
      }

      val stores = buildList {
        if (saveApplicationSettings) {
          add(application)
        }
        addAll(closeBatch.projects)
      }
      if (stores.isNotEmpty()) {
        try {
          application.getServiceIfCreated(FileDocumentManager::class.java)?.saveAllDocuments()
        }
        catch (e: Throwable) {
          ApplicationImpl.logErrorDuringExit("Failed to save documents", e)
        }
        try {
          tracer.spanBuilder("saveSettingsOnExit").use {
            saveHandler.saveSettingsUnderModalProgress(stores)
          }
        }
        catch (e: Throwable) {
          ApplicationImpl.logErrorDuringExit("Failed to save settings", e)
        }
      }
      if (checkCanClose && !closeBatch.confirmCloseAfterSave()) {
        return@use false
      }

      beforeProjectClose.run()
      try {
        tracer.spanBuilder("disposeProjects").use {
          closeBatch.close()
        }
      }
      catch (e: Throwable) {
        ApplicationImpl.logErrorDuringExit("Failed to close and dispose all projects", e)
      }
      true
    }
    if (closed) afterProjectClose.get() else null
  }
}

private object NoProjects : PreparedProjectCloseBatch {
  override val projects: List<Project>
    get() = emptyList()

  override fun close() {
  }
}
