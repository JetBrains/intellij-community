// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.impl.VfsThreadingUtil
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.HashingStrategy
import org.jetbrains.annotations.ApiStatus

/**
 * Republishes VFS events to the background bus ([VirtualFileManager.VFS_CHANGES_BG])
 * if they were not published there yet.
 */
@ApiStatus.Internal
object BackgroundEventSupport {
  private val LOG: Logger = Logger.getInstance(BackgroundEventSupport::class.java)

  private val processedBeforeEvents: MutableSet<List<VFileEvent>> =
    CollectionFactory.createCustomHashingStrategySet(HashingStrategy.identity())
  private val processedAfterEvents: MutableSet<List<VFileEvent>> =
    CollectionFactory.createCustomHashingStrategySet(HashingStrategy.identity())

  /**
   * Starts listening to VFS events on EDT and republishes them to the background bus
   * when they were not published there yet.
   */
  @JvmStatic
  fun startListening() {
    val app = ApplicationManager.getApplication()
    Disposer.register(app, ::ensureAllEventsProcessed)
    val bus = app.getMessageBus()
    val backgroundPublisher = bus.syncPublisher(VirtualFileManager.VFS_CHANGES_BG)
    val connection = bus.simpleConnect()

    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun before(events: List<VFileEvent>) {
        if (processedBeforeEvents.contains(events)) {
          return
        }
        LOG.error(
          "VirtualFile events must not be published directly to VirtualFileManager.VFS_CHANGES. " +
          "Use VirtualFileManager.fireEvents instead: $events" +
          "Events will be republished to VFS_CHANGES_BG topic to maintain backward compatibility"
        )
        markBeforeProcessed(events)
        try {
          VfsThreadingUtil.runActionOnBackgroundRegardlessOfCurrentThread {
            backgroundPublisher.before(events)
          }
        }
        finally {
          unmarkBeforeProcessed(events)
        }
      }

      override fun after(events: List<VFileEvent>) {
        if (processedAfterEvents.contains(events)) {
          return
        }
        LOG.error(
          "VirtualFile events must not be published directly to VirtualFileManager.VFS_CHANGES. " +
          "Use VirtualFileManager.fireEvents instead: $events"
        )
        markAfterProcessed(events)
        try {
          VfsThreadingUtil.runActionOnBackgroundRegardlessOfCurrentThread {
            backgroundPublisher.after(events)
          }
        }
        finally {
          unmarkAfterProcessed(events)
        }
      }
    })

    connection.subscribe(VirtualFileManager.VFS_CHANGES_BG, object : BulkFileListenerBackgroundable {
      override fun before(events: List<VFileEvent>) {
        if (processedBeforeEvents.contains(events)) {
          return
        }
        LOG.error(
          "VirtualFile events must not be published directly to VirtualFileManager.VFS_CHANGES_BG. " +
          "Use VirtualFileManager.fireEvents instead. Duplicate events may be sent: $events"
        )
      }

      override fun after(events: List<VFileEvent>) {
        if (processedAfterEvents.contains(events)) {
          return
        }
        LOG.error(
          "VirtualFile events must not be published directly to VirtualFileManager.VFS_CHANGES_BG. " +
          "Use VirtualFileManager.fireEvents instead. Duplicate events may be sent: $events"
        )
      }
    })
  }

  /**
   * Marks the given event list as already published on background before the events fire.
   * Call this to prevent duplicate background publishing for the [before] phase.
   */
  @JvmStatic
  fun markBeforeProcessed(events: List<VFileEvent>) {
    processedBeforeEvents.add(events)
  }

  /**
   * Marks the given event list as already published on background after the events fire.
   * Call this to prevent duplicate background publishing for the [after] phase.
   */
  @JvmStatic
  fun markAfterProcessed(events: List<VFileEvent>) {
    processedAfterEvents.add(events)
  }

  /**
   * Unmarks the given event list from the before-processed set.
   * Call this to release the tracking entry after the events are fully handled.
   */
  @JvmStatic
  fun unmarkBeforeProcessed(events: List<VFileEvent>) {
    LOG.assertTrue(processedBeforeEvents.remove(events))
  }

  /**
   * Unmarks the given event list from the after-processed set.
   * Call this to release the tracking entry after the events are fully handled.
   */
  @JvmStatic
  fun unmarkAfterProcessed(events: List<VFileEvent>) {
    LOG.assertTrue(processedAfterEvents.remove(events))
  }

  private fun ensureAllEventsProcessed() {
    LOG.assertTrue(
      processedBeforeEvents.isEmpty(),
      "Some VFS before events were not properly processed: $processedBeforeEvents"
    )
    LOG.assertTrue(
      processedAfterEvents.isEmpty(),
      "Some VFS after events were not properly processed: $processedAfterEvents"
    )
  }
}