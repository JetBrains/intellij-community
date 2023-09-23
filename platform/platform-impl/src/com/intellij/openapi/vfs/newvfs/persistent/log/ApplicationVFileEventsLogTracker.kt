// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.openapi.vfs.newvfs.persistent.log.ApplicationVFileEventsTracker.VFileEventTracker
import com.intellij.openapi.vfs.newvfs.persistent.log.VfsLogOperationTrackingContext.Companion.trackOperation

class ApplicationVFileEventsLogTracker(
  private val context: VfsLogOperationTrackingContext
) : ApplicationVFileEventsTracker {

  /**
   * Implementation note:
   * Imagine we're processing a VFileEvent. We started tracking it, allocated space for the start descriptor,
   * some operations took place and their tracking may be already completed, may be not. We wait for the end descriptor's tracking
   * to be completed, before completing the start one.
   * Now imagine the app is killed after we've completed the start descriptor. It is possible that a flush has happened right before the app
   * was killed. This means, that if some operation tracking from within the VFileEvent processing range didn't manage to complete yet,
   * the ready position of OperationsLogStorage will point somewhere between the start descriptor and this operation. If one restarts
   * the application and does not perform a recovery, and uses VFileBasedIterator somewhere, it'll fail, because this start descriptor
   * can't be matched with an end descriptor.
   * TODO this can be fixed by checking recent operations on start, or we can just say "recover after every fail, or
   *        there is no guarantee of correctness" or something
   */

  inner class VFileEventTrackerImpl(
    private val tag: VfsOperationTag,
    private val composeStartOperation: () -> VfsOperation.VFileEventOperation.EventStart
  ) : VFileEventTracker {
    private val eventStartTracker: OperationLogStorage.OperationTracker = context.trackOperation(tag)
    override fun completeEventTracking() {
      context.trackOperation(VfsOperationTag.VFILE_EVENT_END) {
        completeTracking({
          // finish start descriptor only after end is written
          eventStartTracker.completeTracking(null, composeStartOperation)
        }) {
          VfsOperation.VFileEventOperation.EventEnd(tag)
        }
      }
    }
  }

  override fun trackEvent(event: VFileEvent): VFileEventTracker {
    val timestamp = System.currentTimeMillis()
    return when (event) {
      is VFileContentChangeEvent -> {
        val fileId = (event.file as VirtualFileWithId).id
        VFileEventTrackerImpl(VfsOperationTag.VFILE_EVENT_CONTENT_CHANGE) {
          VfsOperation.VFileEventOperation.EventStart.ContentChange(timestamp, fileId)
        }
      }
      is VFileCopyEvent -> {
        val fileId = (event.file as VirtualFileWithId).id
        val newParentId = (event.newParent as VirtualFileWithId).id
        VFileEventTrackerImpl(VfsOperationTag.VFILE_EVENT_COPY) {
          VfsOperation.VFileEventOperation.EventStart.Copy(timestamp, fileId, newParentId)
        }
      }
      is VFileMoveEvent -> {
        val fileId = (event.file as VirtualFileWithId).id
        val oldParentId = (event.oldParent as VirtualFileWithId).id
        val newParentId = (event.newParent as VirtualFileWithId).id
        VFileEventTrackerImpl(VfsOperationTag.VFILE_EVENT_MOVE) {
          VfsOperation.VFileEventOperation.EventStart.Move(timestamp, fileId, oldParentId, newParentId)
        }
      }
      is VFileDeleteEvent -> {
        val fileId = (event.file as VirtualFileWithId).id
        VFileEventTrackerImpl(VfsOperationTag.VFILE_EVENT_DELETE) {
          VfsOperation.VFileEventOperation.EventStart.Delete(timestamp, fileId)
        }
      }
      is VFilePropertyChangeEvent -> {
        val fileId = (event.file as VirtualFileWithId).id
        val propName = event.propertyName
        VFileEventTrackerImpl(VfsOperationTag.VFILE_EVENT_PROPERTY_CHANGED) {
          VfsOperation.VFileEventOperation.EventStart.PropertyChange(timestamp, fileId, propName)
        }
      }
      is VFileCreateEvent -> {
        val parentId = (event.parent as VirtualFileWithId).id
        val isDirectory = event.isDirectory
        val childName = event.childName.toByteArray()
        VFileEventTrackerImpl(VfsOperationTag.VFILE_EVENT_CREATE) {
          val childNameRef = context.payloadWriter(childName)
          VfsOperation.VFileEventOperation.EventStart.Create(timestamp, parentId, childNameRef, isDirectory)
        }
      }
      else -> throw IllegalStateException("unexpected VFileEvent: ${event::class.java}")
    }
  }
}