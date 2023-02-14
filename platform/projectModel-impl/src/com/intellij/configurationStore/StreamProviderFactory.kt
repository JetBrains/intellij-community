// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.ProjectExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Project level extension point.
 *
 * Allows to set custom storage class using providing custom storage specs.
 * Or set custom stream provider for default storage (XmlElementStorage).
 */
@ApiStatus.Internal
interface StreamProviderFactory {
  companion object {
    val EP_NAME = ProjectExtensionPointName<StreamProviderFactory>("com.intellij.streamProviderFactory")
  }

  fun createProvider(componentManager: ComponentManager, storageManager: StateStorageManager): StreamProvider? = null

  /**
   * `storages` are preprocessed by component store - not raw from state spec.
   * @return null if not applicable
   */
  fun customizeStorageSpecs(component: PersistentStateComponent<*>, storageManager: StateStorageManager, stateSpec: State, storages: List<Storage>, operation: StateStorageOperation): List<Storage>? = null

  fun getOrCreateStorageSpec(fileSpec: String, inProjectStateSpec: State? = null): Storage? = null
}