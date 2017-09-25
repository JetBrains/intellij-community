/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.QueueProcessor
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.CalledInBackground

class AsyncVfsEventsPostProcessorImpl : AsyncVfsEventsPostProcessor, Disposable {
  private val LOG = logger<AsyncVfsEventsPostProcessorImpl>()
  private val queue = QueueProcessor<List<VFileEvent>> { events -> processEvents(events) }
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

  @CalledInBackground
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
}
