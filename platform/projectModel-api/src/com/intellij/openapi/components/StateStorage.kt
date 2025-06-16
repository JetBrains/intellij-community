// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import com.intellij.configurationStore.SaveSessionProducer
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
interface StateStorage {
  /**
   * You can call this method only once.
   * If the state exists and is not archived - not-null result.
   * If it doesn't exist or archived - null result.
   */
  fun <T : Any> getState(
    component: Any?,
    componentName: String,
    pluginId: PluginId,
    stateClass: Class<T>,
    mergeInto: T?,
    reload: Boolean,
  ): T?

  /**
   * Returning `null` means that nothing to save.
   */
  fun createSaveSessionProducer(): SaveSessionProducer?

  /**
   * Get changed component names
   */
  fun analyzeExternalChangesAndUpdateIfNeeded(componentNames: MutableSet<in String>)

  fun getResolution(
    component: PersistentStateComponent<*>,
    operation: StateStorageOperation,
    isExternalSystemStorageEnabled: Boolean,
  ): StateStorageChooserEx.Resolution {
    return StateStorageChooserEx.Resolution.DO
  }
}

@Internal
interface StateStorageChooserEx {
  enum class Resolution { DO, SKIP, CLEAR }

  fun getResolution(storage: Storage, operation: StateStorageOperation): Resolution
}