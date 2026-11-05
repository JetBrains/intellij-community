// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/snapshot-cache.spec.md

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.settings.v2.impl.cache.SettingsCacheStorage
import com.intellij.platform.settings.v2.impl.cache.SettingsSnapshotCache
import com.intellij.platform.settings.v2.impl.cache.isSnapshotCacheEnabled
import kotlinx.coroutines.CoroutineScope

/**
 * Holds the snapshot cache of one project. It seeds the layers of the project, the project context and its files, on
 * open, then persists them. [ProjectSettingsLoadActivity] creates it on open and calls [load] before the reload. Its
 * project scope keeps the debounced saver alive until the close, when [SettingsProjectListener] calls [saveIfDirty].
 *
 * The cache is off in unit tests, because the shared system folder of the project would leak state across
 * `com.intellij.testFramework.junit5.TestApplication` runs. The tests drive [SettingsSnapshotCache] directly against
 * a temporary file.
 */
@Service(Service.Level.PROJECT)
internal class ProjectSettingsCacheService(private val project: Project, scope: CoroutineScope) {
  /** Holds the cache, or `null` when it is off ([isSnapshotCacheEnabled]). Then every operation does nothing. */
  private val cache =
    if (isSnapshotCacheEnabled()) SettingsSnapshotCache(SettingsCacheStorage.projectCacheFile(project), project = project) else null

  init {
    cache?.startSaving(scope)
  }

  /** Seeds the vault from the cache of the project. See [SettingsSnapshotCache.load]. */
  suspend fun load() {
    cache?.load()
  }

  /** Writes the cache of the project when a change is pending since the last save. See [SettingsSnapshotCache.saveIfDirty]. */
  suspend fun saveIfDirty() {
    cache?.saveIfDirty()
  }

  companion object {
    fun getInstance(project: Project): ProjectSettingsCacheService = project.service()
  }
}
