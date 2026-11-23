// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl

// @spec ../../../../../../../../../spec/settings-writes.spec.md
// @spec ../../../../../../../../../spec/snapshot-cache.spec.md

import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking

/**
 * Runs the shutdown save under a modal progress that the user cannot cancel. The save must complete, and it can block
 * for a short time. The progress shows the user that the IDE is busy.
 *
 * Registered as an application service in `intellij.platform.settings.v2.shared.essential.xml`.
 */
internal class ModalShutdownSaver : ShutdownSaver {
  override fun save() {
    runWithModalProgressBlocking(
      ModalTaskOwner.guess(),
      SettingsV2Bundle.message("progress.title.saving.settings"),
      TaskCancellation.nonCancellable(),
    ) {
      saveAppSettingsAndCache()
    }
  }
}
