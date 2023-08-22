// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

interface SaveSession : StorageManagerFileWriteRequestor {
  fun save()
}

interface SaveSessionProducer : StorageManagerFileWriteRequestor {
  fun setState(component: Any?, componentName: String, state: Any?)

  /**
   * return null if nothing to save
   */
  fun createSaveSession(): SaveSession?
}

/**
 * A marker interface for to not process this file change event.
 */
interface StorageManagerFileWriteRequestor