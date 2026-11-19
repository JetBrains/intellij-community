// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/settings-contexts.spec.md

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.jetbrains.annotations.ApiStatus

/**
 * Holds the urls of the roots a settings context anchors on: the topmost content roots of the project, with every
 * nested one dropped. See [topmostContentRoots].
 *
 * The urls are computed on first use and dropped when the project model changes. The cache is the only listener of
 * that change in settings v2. `BackendContentRootsApi` in `intellij.platform.settings.v2.backend.split` follows
 * [rootsChanged] instead of its own listener, because two listeners of one event run in no defined order.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SettingsRootsCache(private val project: Project, scope: CoroutineScope) {
  @Volatile
  private var rootUrls: List<String>? = null

  private val rootsChangedSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  /**
   * Emits after the cache drops the urls on a change of the project roots. The signal is conflated and holds no
   * roots: a collector reads [rootUrls] after it.
   */
  val rootsChanged: SharedFlow<Unit> = rootsChangedSignal.asSharedFlow()

  init {
    project.messageBus.connect(scope).subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        rootUrls = null
        rootsChangedSignal.tryEmit(Unit)
      }
    })
  }

  /** Returns the urls of the topmost content roots of the project, sorted. */
  @RequiresReadLock
  fun rootUrls(): List<String> = rootUrls ?: project.topmostContentRoots().map { it.url }.also { rootUrls = it }

  companion object {
    fun getInstance(project: Project): SettingsRootsCache = project.service()
  }
}

/**
 * Supplies the roots of [SettingsRootsCache] to a file context in the monolith and on the backend. Its frontend twin
 * is `FrontendContentRootsProvider` in `intellij.platform.settings.v2.frontend.split`.
 */
internal class ProjectModelContentRootsProvider : SettingsContentRootsProvider {
  @RequiresReadLock
  override fun rootUrls(project: Project): List<String> = SettingsRootsCache.getInstance(project).rootUrls()
}
