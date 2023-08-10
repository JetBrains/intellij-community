// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SingleAlarm
import org.gradle.tooling.ProjectConnection
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Temporarily store latest virtual files written from documents.
 *
 * Used to report latest changes to Gradle Daemon.
 * Will skip wrapper download progress events.
 */
@Service
@ApiStatus.Experimental
class GradleFileModificationTracker: Disposable {
  private val myCacheRef = AtomicReference<MutableSet<Path>>(ConcurrentHashMap.newKeySet())
  private val alarm = SingleAlarm.pooledThreadSingleAlarm(5000, this) {
    myCacheRef.set(ConcurrentHashMap.newKeySet())
  }

  /**
   * If called when wrapper is not yet available, wrapper download events will be lost!
   * Make sure, wrapper is already downloaded in the call site
   */
  fun notifyConnectionAboutChangedPaths(connection: ProjectConnection) {
    val collection = myCacheRef.getAndSet(ConcurrentHashMap.newKeySet()).toList()
    if (collection.isNotEmpty()) {
      connection.notifyDaemonsAboutChangedPaths(collection)
    }
  }

  fun beforeSaving(virtualFile: VirtualFile) {
    val vfs = virtualFile.fileSystem
    vfs.getNioPath(virtualFile)?.let {
      myCacheRef.get().add(it)
    }
    alarm.cancelAndRequest()
  }

  override fun dispose() {
    // nothing to do, just dispose the alarm
  }
}

internal class GradleFileModificationListener: FileDocumentManagerListener {
  override fun beforeDocumentSaving(document: Document) {
    val modificationTracker = ApplicationManager.getApplication().getService(GradleFileModificationTracker::class.java)
    FileDocumentManager.getInstance().getFile(document)?.let { modificationTracker.beforeSaving(it) }
  }
}