// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.components.*
import com.intellij.util.messages.Topic

val STORAGE_TOPIC: Topic<StorageManagerListener> = Topic("STORAGE_LISTENER", StorageManagerListener::class.java, Topic.BroadcastDirection.TO_PARENT)

interface StateStorageManager {
  val macroSubstitutor: TrackingPathMacroSubstitutor?
    get() = null

  val componentManager: ComponentManager?

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

interface StorageCreator {
  val key: String

  fun create(storageManager: StateStorageManager): StateStorage
}