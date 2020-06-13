// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components

import com.intellij.configurationStore.SaveSessionProducer

interface StateStorage {
  val isUseVfsForWrite: Boolean
    get() = false

  /**
   * You can call this method only once.
   * If state exists and not archived - not-null result.
   * If doesn't exists or archived - null result.
   */
  fun <T : Any> getState(component: Any?, componentName: String, stateClass: Class<T>, mergeInto: T?, reload: Boolean): T?

  fun hasState(componentName: String, reloadData: Boolean): Boolean

  /**
   * Returning `null` means that nothing to save.
   */
  fun createSaveSessionProducer(): SaveSessionProducer?

  /**
   * Get changed component names
   */
  fun analyzeExternalChangesAndUpdateIfNeeded(componentNames: MutableSet<String>)

  fun getResolution(component: PersistentStateComponent<*>, operation: StateStorageOperation): StateStorageChooserEx.Resolution {
    return StateStorageChooserEx.Resolution.DO
  }
}