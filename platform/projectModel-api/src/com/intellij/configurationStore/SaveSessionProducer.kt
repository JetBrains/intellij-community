// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.extensions.PluginId

import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.annotations.ApiStatus

interface SaveSession : StorageManagerFileWriteRequestor {
  suspend fun save(events: MutableList<VFileEvent>?)
}

interface SaveSessionProducer : StorageManagerFileWriteRequestor {
  fun setState(component: Any?, componentName: String, pluginId: PluginId, state: Any?)

  /**
   * Returns `null` if nothing to save.
   */
  fun createSaveSession(): SaveSession?
}

/**
 * A marker interface to skip processing of this file change event.
 */
@ApiStatus.Internal
interface StorageManagerFileWriteRequestor
