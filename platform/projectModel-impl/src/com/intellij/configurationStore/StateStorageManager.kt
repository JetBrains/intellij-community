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

import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.util.messages.Topic

val STORAGE_TOPIC = Topic("STORAGE_LISTENER", StorageManagerListener::class.java, Topic.BroadcastDirection.TO_PARENT)

interface StateStorageManager {
  val macroSubstitutor: TrackingPathMacroSubstitutor?
    get() = null

  fun getStateStorage(storageSpec: Storage): StateStorage

  fun addStreamProvider(provider: StreamProvider, first: Boolean = false)

  fun removeStreamProvider(clazz: Class<out StreamProvider>)

  /**
   * Rename file
   * @param path System-independent full old path (/project/bar.iml or collapse $MODULE_FILE$)
   * *
   * @param newName Only new file name (foo.iml)
   */
  fun rename(path: String, newName: String)

  fun startExternalization(): ExternalizationSession?

  fun getOldStorage(component: Any, componentName: String, operation: StateStorageOperation): StateStorage?

  fun expandMacros(path: String): String

  interface ExternalizationSession {
    fun setState(storageSpecs: List<Storage>, component: Any, componentName: String, state: Any)

    fun setStateInOldStorage(component: Any, componentName: String, state: Any)

    /**
     * return empty list if nothing to save
     */
    fun createSaveSessions(): List<StateStorage.SaveSession>
  }
}