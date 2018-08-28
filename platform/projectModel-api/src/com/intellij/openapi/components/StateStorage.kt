// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components

import java.io.IOException

interface StateStorage {
  /**
   * You can call this method only once.
   * If state exists and not archived - not-null result.
   * If doesn't exists or archived - null result.
   */
  fun <T : Any> getState(component: Any?, componentName: String, stateClass: Class<T>, mergeInto: T?, reload: Boolean): T?

  fun hasState(componentName: String, reloadData: Boolean): Boolean

  fun createSaveSessionProducer(): SaveSessionProducer?

  /**
   * Get changed component names
   */
  fun analyzeExternalChangesAndUpdateIfNeed(componentNames: MutableSet<String>)

  fun getResolution(component: PersistentStateComponent<*>, operation: StateStorageOperation): StateStorageChooserEx.Resolution {
    return StateStorageChooserEx.Resolution.DO
  }

  interface SaveSessionProducer {
    @Throws(IOException::class)
    fun setState(component: Any?, componentName: String, state: Any?)

    /**
     * return null if nothing to save
     */
    fun createSaveSession(): SaveSession?
  }

  interface SaveSession {
    @Throws(IOException::class)
    fun save()
  }
}