// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

enum class VfsOperationTag {
  NULL,

  REC_ALLOC,
  REC_SET_ATTR_REC_ID,
  REC_SET_CONTENT_RECORD_ID,
  REC_SET_PARENT,
  REC_SET_NAME_ID,
  REC_SET_FLAGS,
  REC_SET_LENGTH,
  REC_SET_TIMESTAMP,
  REC_MARK_RECORD_AS_MODIFIED,
  REC_FILL_RECORD,
  REC_CLEAN_RECORD,
  REC_SET_VERSION,

  ATTR_WRITE_ATTR,
  ATTR_DELETE_ATTRS,
  ATTR_SET_VERSION,

  CONTENT_WRITE_BYTES,
  CONTENT_WRITE_STREAM,
  CONTENT_WRITE_STREAM_2,
  CONTENT_APPEND_STREAM,
  CONTENT_REPLACE_BYTES,
  CONTENT_ACQUIRE_NEW_RECORD,
  CONTENT_ACQUIRE_RECORD,
  CONTENT_RELEASE_RECORD,
  CONTENT_SET_VERSION,

  VFILE_EVENT_CONTENT_CHANGE,
  VFILE_EVENT_COPY,
  VFILE_EVENT_CREATE,
  VFILE_EVENT_DELETE,
  VFILE_EVENT_MOVE,
  VFILE_EVENT_PROPERTY_CHANGED,
  VFILE_EVENT_END;

  companion object {
    const val SIZE_BYTES = Byte.SIZE_BYTES
  }
}

val VfsOperationTag.isRecordOperation get() = VfsOperationTag.REC_ALLOC <= this && this <= VfsOperationTag.REC_SET_VERSION
val VfsOperationTag.isAttributeOperation get() = VfsOperationTag.ATTR_WRITE_ATTR <= this && this <= VfsOperationTag.ATTR_SET_VERSION
val VfsOperationTag.isContentOperation get() = VfsOperationTag.CONTENT_WRITE_BYTES <= this && this <= VfsOperationTag.CONTENT_SET_VERSION
val VfsOperationTag.isVFileEventOperation get() = VfsOperationTag.VFILE_EVENT_CONTENT_CHANGE <= this && this <= VfsOperationTag.VFILE_EVENT_END