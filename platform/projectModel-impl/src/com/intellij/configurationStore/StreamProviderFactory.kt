/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.configurationStore

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.Storage
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
  fun customizeStorageSpecs(component: PersistentStateComponent<*>, componentManager: ComponentManager, storages: List<Storage>, operation: StateStorageOperation): List<Storage>? = null
}