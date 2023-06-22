// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.*
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

interface StateStorageManager {
  val macroSubstitutor: PathMacroSubstitutor?
    get() = null

  val componentManager: ComponentManager?

  fun getStateStorage(storageSpec: Storage): StateStorage

  fun addStreamProvider(provider: StreamProvider, first: Boolean = false)

  fun removeStreamProvider(aClass: Class<out StreamProvider>)

  fun getOldStorage(component: Any, componentName: String, operation: StateStorageOperation): StateStorage?

  fun expandMacro(collapsedPath: String): Path

  @ApiStatus.ScheduledForRemoval
  @Deprecated(level = DeprecationLevel.ERROR, message = "Use expandMacro(collapsedPath)", replaceWith = ReplaceWith("expandMacro(collapsedPath)"))
  fun expandMacros(collapsedPath: String): String = expandMacro(collapsedPath).systemIndependentPath
}

interface RenameableStateStorageManager {
  /**
   * @param newName Only new file name (foo.iml)
   */
  fun rename(newName: String)

  fun pathRenamed(newPath: Path, event: VFileEvent?)
}

interface StorageCreator {
  val key: String

  fun create(storageManager: StateStorageManager): StateStorage
}

// no need to fire events for known requestors - all current subscribers are not interested in internal changes,
// better to reduce message bus usage
fun isFireStorageFileChangedEvent(event: VFileEvent): Boolean {
  // ignore VFilePropertyChangeEvent because doesn't affect content
  return event !is VFilePropertyChangeEvent && event.requestor !is StorageManagerFileWriteRequestor
}