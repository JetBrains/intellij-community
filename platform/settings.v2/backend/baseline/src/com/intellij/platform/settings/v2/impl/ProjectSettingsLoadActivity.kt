// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/layer-loading.spec.md
// @spec ../../../../../../../../spec/snapshot-cache.spec.md

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsVault

/**
 * Loads the settings when a project opens. It seeds the model from the snapshot cache of the project for a fast first
 * paint. Then [SettingsVault.reload] asks every layer provider for the project scope and installs what they return.
 * This reconciles the seeded cache with the real sources. On the way, each provider creates its per-project service,
 * and that service registers its file watcher. So a provider needs no open hook of its own.
 *
 * The reload ends only after indexing, because the file layer waits for the file name index (ADR 0163). The platform
 * runs each [ProjectActivity] in its own coroutine, and indexing does not wait for this activity. So the wait blocks
 * no other startup activity and no indexing. The application and the project contexts get ready before the index.
 */
internal class ProjectSettingsLoadActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    ProjectSettingsCacheService.getInstance(project).load()
    SettingsVault.getInstance().reload(SettingsContext.project(project))
  }
}
