// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.QueueProcessor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.TestOnly

private val LOG = logger<AsyncVfsEventsPostProcessorImpl>()

class AsyncVfsEventsPostProcessorImpl : AsyncVfsEventsPostProcessor, Disposable {
  private val queue = QueueProcessor(::processEvents)
  private val messageBus = ApplicationManager.getApplication().messageBus

  private data class ListenerAndDisposable(val listener: AsyncVfsEventsListener, val disposable: Disposable)
  private val listeners = ContainerUtil.createConcurrentList<ListenerAndDisposable>()

  init {
    messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        queue.add(events)
      }
    })
  }

  override fun addListener(listener: AsyncVfsEventsListener, disposable: Disposable) {
    val element = ListenerAndDisposable(listener, disposable)
    Disposer.register(disposable, Disposable { listeners.remove(element) })
    listeners.add(element)
  }

  override fun dispose() {
    queue.clear()
    listeners.clear()
  }

  @RequiresBackgroundThread
  private fun processEvents(events: List<VFileEvent>) {
    for ((listener, parentDisposable) in listeners) {
      try {
        BackgroundTaskUtil.runUnderDisposeAwareIndicator(parentDisposable, Runnable {
          listener.filesChanged(events)
        })
      }
      catch(pce: ProcessCanceledException) {
        // move to the next task
      }
      catch(e: Throwable) {
        LOG.error(e)
      }
    }
  }

  companion object {
    @JvmStatic
    @TestOnly
    fun waitEventsProcessed() {
      assert(ApplicationManager.getApplication().isUnitTestMode)
      (AsyncVfsEventsPostProcessor.getInstance() as AsyncVfsEventsPostProcessorImpl).queue.waitFor()
    }
  }
}
