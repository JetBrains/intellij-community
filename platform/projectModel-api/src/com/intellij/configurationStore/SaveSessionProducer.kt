// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

interface SaveSession : StorageManagerFileWriteRequestor {
  suspend fun save() {
    saveBlocking()
  }

  fun saveBlocking()
}

interface SaveSessionProducer : StorageManagerFileWriteRequestor {
  fun setState(component: Any?, componentName: String, state: Any?)

  /**
   * Returns `null` if nothing to save.
   */
  fun createSaveSession(): SaveSession?
}

/**
 * A marker interface to skip processing of this file change event.
 */
interface StorageManagerFileWriteRequestor
