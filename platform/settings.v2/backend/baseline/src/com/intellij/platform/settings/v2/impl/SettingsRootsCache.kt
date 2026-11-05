// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/settings-contexts.spec.md

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CoroutineScope

/**
 * Holds the roots a settings context anchors on: the topmost content roots of the project, with every nested one
 * dropped. See [topmostContentRoots].
 *
 * The roots are computed on first use and dropped when the project model changes.
 */
@Service(Service.Level.PROJECT)
internal class SettingsRootsCache(private val project: Project, scope: CoroutineScope) {
  @Volatile
  private var roots: List<VirtualFile>? = null

  init {
    project.messageBus.connect(scope).subscribe(ModuleRootListener.TOPIC, object : ModuleRootListener {
      override fun rootsChanged(event: ModuleRootEvent) {
        roots = null
      }
    })
  }

  /**
   * Returns the root that holds [file], or `null` when no root does.
   *
   * The match is on the url, so the file system counts: a jar entry sits at a path under the project, and it
   * belongs to no content root.
   */
  @RequiresReadLock
  fun rootFor(file: VirtualFile): VirtualFile? {
    val url = file.url
    return roots().firstOrNull { url == it.url || url.startsWith("${it.url}/") }
  }

  /** Returns the topmost content roots of the project, sorted by url. */
  @RequiresReadLock
  fun roots(): List<VirtualFile> = roots ?: project.topmostContentRoots().also { roots = it }

  companion object {
    fun getInstance(project: Project): SettingsRootsCache = project.service()
  }
}
