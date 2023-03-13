// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.events.*

class VFileEventApplicationLogListener(
  private val context: VfsLogContext
) : VFileEventApplicationListener {
  override fun beforeApply(event: VFileEvent) {
    val timestamp = System.currentTimeMillis()
    when (event) {
      is VFileContentChangeEvent -> {
        val fileId = (event.file as VirtualFileWithId).id
        context.enqueueDescriptorWrite(VfsOperationTag.VFILE_EVENT_CONTENT_CHANGE) {
          VfsOperation.VFileEventOperation.EventStart.ContentChange(timestamp, fileId)
        }
      }
      is VFileCopyEvent -> {
        val fileId = (event.file as VirtualFileWithId).id
        val newParentId = (event.newParent as VirtualFileWithId).id
        context.enqueueDescriptorWrite(VfsOperationTag.VFILE_EVENT_COPY) {
          VfsOperation.VFileEventOperation.EventStart.Copy(timestamp, fileId, newParentId)
        }
      }
      is VFileMoveEvent -> {
        val fileId = (event.file as VirtualFileWithId).id
        val oldParentId = (event.oldParent as VirtualFileWithId).id
        val newParentId = (event.newParent as VirtualFileWithId).id
        context.enqueueDescriptorWrite(VfsOperationTag.VFILE_EVENT_MOVE) {
          VfsOperation.VFileEventOperation.EventStart.Move(timestamp, fileId, oldParentId, newParentId)
        }
      }
      is VFileDeleteEvent -> {
        val fileId = (event.file as VirtualFileWithId).id
        context.enqueueDescriptorWrite(VfsOperationTag.VFILE_EVENT_DELETE) {
          VfsOperation.VFileEventOperation.EventStart.Delete(timestamp, fileId)
        }
      }
      is VFilePropertyChangeEvent -> {
        val fileId = (event.file as VirtualFileWithId).id
        val propName = event.propertyName
        context.enqueueDescriptorWrite(VfsOperationTag.VFILE_EVENT_PROPERTY_CHANGED) {
          VfsOperation.VFileEventOperation.EventStart.PropertyChange(timestamp, fileId, propName)
        }
      }
      is VFileCreateEvent -> {
        val parentId = (event.parent as VirtualFileWithId).id
        val isDirectory = event.isDirectory
        val childName = event.childName.toByteArray()
        context.enqueueDescriptorWrite(VfsOperationTag.VFILE_EVENT_CREATE) {
          val childNameRef = payloadStorage.writePayload(childName.size.toLong()) {
            write(childName)
          }
          VfsOperation.VFileEventOperation.EventStart.Create(timestamp, parentId, childNameRef, isDirectory)
        }
      }
      else -> throw IllegalStateException("unexpected VFileEvent: ${javaClass}")
    }
  }

  override fun afterApply(event: VFileEvent, throwable: Throwable?) {
    val tag = when (event) {
      is VFileContentChangeEvent -> VfsOperationTag.VFILE_EVENT_CONTENT_CHANGE
      is VFileCopyEvent -> VfsOperationTag.VFILE_EVENT_COPY
      is VFileMoveEvent -> VfsOperationTag.VFILE_EVENT_MOVE
      is VFileDeleteEvent -> VfsOperationTag.VFILE_EVENT_DELETE
      is VFilePropertyChangeEvent -> VfsOperationTag.VFILE_EVENT_PROPERTY_CHANGED
      is VFileCreateEvent -> VfsOperationTag.VFILE_EVENT_CREATE
      else -> throw IllegalStateException("unexpected VFileEvent: ${javaClass}")
    }
    val result =
      if (throwable != null) OperationResult.fromException(throwable.javaClass.name)
      else OperationResult.fromValue(Unit)
    context.enqueueDescriptorWrite(VfsOperationTag.VFILE_EVENT_END) {
      VfsOperation.VFileEventOperation.EventEnd(tag, result)
    }
  }
}