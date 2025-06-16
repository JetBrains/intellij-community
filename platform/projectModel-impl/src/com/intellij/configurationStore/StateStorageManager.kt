// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.*
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.xmlb.SettingsInternalApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path

@ApiStatus.NonExtendable
@Internal
interface StateStorageManager {
  val macroSubstitutor: PathMacroSubstitutor?
    get() = null

  val componentManager: ComponentManager?

  fun getStateStorage(storageSpec: Storage): StateStorage

  fun addStreamProvider(provider: StreamProvider, first: Boolean = false)

  fun removeStreamProvider(aClass: Class<out StreamProvider>)

  fun getOldStorage(component: Any, componentName: String, operation: StateStorageOperation): StateStorage?

  fun expandMacro(collapsedPath: String): Path

  fun collapseMacro(path: String): String

  val streamProvider: StreamProvider

  @SettingsInternalApi
  fun clearStorages() {
  }

  @SettingsInternalApi
  fun release() {
  }

  val isExternalSystemStorageEnabled: Boolean
    get() = false
}

@Internal
interface RenameableStateStorageManager {
  /**
   * @param newName Only new file name (foo.iml)
   */
  fun rename(newName: String)

  fun pathRenamed(newPath: Path, event: VFileEvent?)
}

@Internal
interface StorageCreator {
  val key: String

  fun create(storageManager: StateStorageManager): StateStorage
}

// no need to fire events for known requestors - all current subscribers are not interested in internal changes,
// better to reduce message bus usage
@Internal
fun isFireStorageFileChangedEvent(event: VFileEvent): Boolean {
  // ignore VFilePropertyChangeEvent because doesn't affect content
  return event !is VFilePropertyChangeEvent && event.requestor !is StorageManagerFileWriteRequestor
}