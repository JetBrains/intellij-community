// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../spec/settings-writes.spec.md
// @spec ../../../../../../../../spec/snapshot-cache.spec.md

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsVault

/**
 * Flushes the pending debounced settings saves of a project to the disk before the project closes, so a recent
 * change is not lost. Then it saves the snapshot cache of the project. It runs on
 * [ProjectCloseListener.projectClosing], before any teardown, while the vault and every provider are still alive. It
 * runs under a non-cancellable modal progress, because the save must complete and may block for a short time.
 *
 * It does not depend on a provider. [SettingsVault.flush] works by context, not by provider, so a flush of the
 * project scope covers the project layer and every file config under it. `SettingsAppLifecycleListener` of the shared
 * module handles the application shutdown.
 *
 * `intellij.platform.settings.v2.backend.xml` registers it as an application listener, because
 * [ProjectCloseListener.TOPIC] is an application-level topic.
 */
internal class SettingsProjectListener : ProjectCloseListener {
  override fun projectClosing(project: Project) {
    runWithModalProgressBlocking(
      ModalTaskOwner.project(project),
      SettingsV2Bundle.message("progress.title.saving.project.settings"),
      TaskCancellation.nonCancellable(),
    ) {
      SettingsVault.getInstance().flush(SettingsContext.project(project))
      ProjectSettingsCacheService.getInstance(project).saveIfDirty()
    }
  }
}
