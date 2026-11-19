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

/**
 * Holds the urls of the roots a settings context anchors on: the topmost content roots of the project, with every
 * nested one dropped. See [topmostContentRoots].
 *
 * The urls are computed on first use and dropped when the project model changes.
 */
@Service(Service.Level.PROJECT)
internal class SettingsRootsCache(private val project: Project, scope: CoroutineScope) {
  @Volatile
  private var rootUrls: List<String>? = null

  init {
    project.messageBus.connect(scope).subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        rootUrls = null
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
