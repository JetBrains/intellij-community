// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.ProjectBulkFileListener
import com.intellij.openapi.vfs.ProjectBulkFileListenerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

internal class ProjectBulkFileListenerRegistryImpl : ProjectBulkFileListenerRegistry, Disposable {
  private val runtimeListeners = CopyOnWriteArrayList<ProjectBulkFileListener>()

  override fun addListener(listener: ProjectBulkFileListener, parentDisposable: Disposable) {
    Disposer.register(parentDisposable) { runtimeListeners.remove(listener) }
    runtimeListeners.add(listener)
  }

  override fun addListener(coroutineScope: CoroutineScope, listener: ProjectBulkFileListener) {
    runtimeListeners.add(listener)
    coroutineScope.coroutineContext.job.invokeOnCompletion { runtimeListeners.remove(listener) }
  }

  private fun hasListeners(): Boolean = runtimeListeners.isNotEmpty() || EP_NAME.hasAnyExtensions()

  internal fun snapshotListeners(): List<ProjectBulkFileListener> {
    return Collections.unmodifiableList(EP_NAME.extensionList + runtimeListeners)
  }

  override fun dispose() {
    runtimeListeners.clear()
  }

  companion object {
    private val EP_NAME: ExtensionPointName<ProjectBulkFileListener> = ExtensionPointName("com.intellij.vfs.projectBulkListener")


    internal fun getInstanceImpl(project: Project): ProjectBulkFileListenerRegistryImpl? {
      val service = project.serviceIfCreated<ProjectBulkFileListenerRegistry>() as? ProjectBulkFileListenerRegistryImpl
      if (service != null) return service.takeIf { it.hasListeners() }
      if (!EP_NAME.hasAnyExtensions()) return null
      return ProjectBulkFileListenerRegistry.getInstance(project) as? ProjectBulkFileListenerRegistryImpl
    }
  }
}
