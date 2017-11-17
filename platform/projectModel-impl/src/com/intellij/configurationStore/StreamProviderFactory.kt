// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Project level extension point.
 *
 * Allows to set custom storage class using providing custom storage specs.
 * Or set custom stream provider for default storage (XmlElementStorage).
 */
interface StreamProviderFactory {
  companion object {
    val EP_NAME = ExtensionPointName.create<StreamProviderFactory>("com.intellij.streamProviderFactory")
  }

  fun createProvider(componentManager: ComponentManager, storageManager: StateStorageManager): StreamProvider? = null

  /**
   * `storages` are preprocessed by component store - not raw from state spec.
   * @return null if not applicable
   */
  fun customizeStorageSpecs(component: PersistentStateComponent<*>, componentManager: ComponentManager, stateSpec: State, storages: List<Storage>, operation: StateStorageOperation): List<Storage>? = null
}