// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Supplies the mode of [SmartModeScheduler] for the modules that this process loads.
 *
 * The first registered extension wins. When no extension is registered, the mode is smart.
 * This is an extension point and not a service, so the scheduler keeps its queue when a module comes or goes with
 * a dynamic reconfiguration, and the module stays dynamically loadable.
 */
@Internal
interface SmartModeProvider {
  /**
   * @return a bit set of [SmartModeScheduler.SCANNING] and [SmartModeScheduler.DUMB] for [project]. Zero means smart mode.
   */
  fun getCurrentMode(project: Project): Int

  /**
   * Emits an element after each change of [getCurrentMode] for [project].
   */
  fun modeChanged(project: Project): Flow<*>

  companion object {
    val EP_NAME: ExtensionPointName<SmartModeProvider> = ExtensionPointName("com.intellij.smartModeProvider")
  }
}

/**
 * Reports smart mode at all times.
 * The split frontend registers it, so a runnable does not wait for the dumb mode that the frontend mirrors from the backend.
 */
@Internal
class AlwaysSmartModeProvider : SmartModeProvider {
  override fun getCurrentMode(project: Project): Int = 0

  override fun modeChanged(project: Project): Flow<*> = emptyFlow<Nothing>()
}

/**
 * Reports dumb mode at all times.
 * JetBrains Light registers it with `order="last"`, so a runnable waits for the upgrade, and the provider of the new mode wins.
 */
@Internal
class AlwaysDumbModeProvider : SmartModeProvider {
  override fun getCurrentMode(project: Project): Int = SmartModeScheduler.DUMB

  override fun modeChanged(project: Project): Flow<*> = emptyFlow<Nothing>()
}
