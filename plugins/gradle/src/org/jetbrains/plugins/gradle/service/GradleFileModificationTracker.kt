// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.gradle.tooling.ProjectConnection
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

/**
 * Temporarily store the latest virtual files written from documents.
 *
 * Used to report latest changes to Gradle Daemon.
 * Will skip wrapper download progress events.
 */
@OptIn(FlowPreview::class)
@Service
@ApiStatus.Experimental
class GradleFileModificationTracker(coroutineScope: CoroutineScope) {
  private val cacheRef = AtomicReference<MutableSet<Path>>(ConcurrentHashMap.newKeySet())

  // This runnable does not interact with project configuration, it should not carry context
  private val updateCacheRefRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch {
      updateCacheRefRequests
        .debounce(5.seconds)
        .collect {
          cacheRef.set(ConcurrentHashMap.newKeySet())
        }
    }
  }

  /**
   * If called when wrapper is not yet available, wrapper download events will be lost!
   * Make sure, wrapper is already downloaded in the call site
   */
  fun notifyConnectionAboutChangedPaths(connection: ProjectConnection) {
    val collection = cacheRef.getAndSet(ConcurrentHashMap.newKeySet()).toList()
    if (collection.isNotEmpty()) {
      connection.notifyDaemonsAboutChangedPaths(collection)
    }
  }

  fun beforeSaving(virtualFile: VirtualFile) {
    val vfs = virtualFile.fileSystem
    vfs.getNioPath(virtualFile)?.let {
      cacheRef.get().add(it)
    }
    check(updateCacheRefRequests.tryEmit(Unit))
  }
}

private class GradleFileModificationListener : FileDocumentManagerListener {
  override fun beforeDocumentSaving(document: Document) {
    val modificationTracker = ApplicationManager.getApplication().getService(GradleFileModificationTracker::class.java)
    FileDocumentManager.getInstance().getFile(document)?.let { modificationTracker.beforeSaving(it) }
  }
}