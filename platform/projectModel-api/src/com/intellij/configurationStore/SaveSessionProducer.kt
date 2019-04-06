// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import java.io.IOException

interface SaveSession : StorageManagerFileWriteRequestor {
  @Throws(IOException::class)
  fun save()
}

interface SaveSessionProducer : StorageManagerFileWriteRequestor {
  @Throws(IOException::class)
  fun setState(component: Any?, componentName: String, state: Any?)

  /**
   * return null if nothing to save
   */
  fun createSaveSession(): SaveSession?
}

/**
 * A marker interface for [FileUndoProvider] to not process this file change event.
 */
interface StorageManagerFileWriteRequestor