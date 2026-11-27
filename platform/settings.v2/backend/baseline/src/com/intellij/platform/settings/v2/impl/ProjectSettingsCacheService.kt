// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../../spec/snapshot-cache.spec.md
// @spec ../../../../../../../../../spec/settings-writes.spec.md

import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsVault
import com.intellij.platform.settings.v2.impl.cache.SettingsCacheStorage
import com.intellij.platform.settings.v2.impl.cache.SettingsSnapshotCache
import com.intellij.platform.settings.v2.impl.cache.isSnapshotCacheEnabled
import kotlinx.coroutines.CoroutineScope

/**
 * Holds the snapshot cache of one project. It seeds the layers of the project, the project context and its files, on
 * open, then persists them. [ProjectSettingsLoadActivity] creates it on open and calls [load] before the reload. Its
 * project scope keeps the debounced saver alive until the close, when [SettingsProjectListener] calls [saveIfDirty].
 *
 * It is also the settings v2 part of a settings save of the project, see [save]. It owns that part because it lives
 * from the open to the close of the project, and the store calls only a created service.
 *
 * The cache is off in unit tests, because the shared system folder of the project would leak state across
 * `com.intellij.testFramework.junit5.TestApplication` runs. The tests drive [SettingsSnapshotCache] directly against
 * a temporary file.
 */
@Service(Service.Level.PROJECT)
internal class ProjectSettingsCacheService(private val project: Project, scope: CoroutineScope) : SettingsSavingComponent {
  /** Holds the cache, or `null` when it is off ([isSnapshotCacheEnabled]). Then every cache operation does nothing. */
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

  /**
   * Flushes the project scope, and so the project layer and the file configs under it, in a settings save of the
   * project. The project store calls it in `ComponentStoreWithExtraComponents.saveSettingsAndCommitComponents` of the module
   * `intellij.platform.configurationStore.impl`. So a config file is current on the disk whenever the XML settings
   * of the project are, for example before a commit.
   */
  override suspend fun save() {
    SettingsVault.getInstance().flush(SettingsContext.project(project))
  }

  companion object {
    fun getInstance(project: Project): ProjectSettingsCacheService = project.service()
  }
}
