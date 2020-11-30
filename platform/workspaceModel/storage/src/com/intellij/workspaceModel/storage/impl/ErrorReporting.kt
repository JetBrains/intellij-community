// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import java.io.ByteArrayOutputStream
import java.io.OutputStream

internal inline fun createAttachment(path: String,
                                     displayText: String,
                                     howToSerialize: (EntityStorageSerializerImpl, OutputStream) -> Unit): Attachment {
  val stream = ByteArrayOutputStream()
  val serializer = EntityStorageSerializerImpl(SimpleEntityTypesResolver, VirtualFileUrlManagerImpl())
  howToSerialize(serializer, stream)
  val bytes = stream.toByteArray()
  return createAttachment(path, bytes, displayText)
}

fun WorkspaceEntityStorage.asAttachment(path: String, displayText: String): Attachment {
  return createAttachment(path, displayText) { serializer, stream ->
    serializer.serializeCache(stream, this.makeSureItsStore())
  }
}

private fun WorkspaceEntityStorage.makeSureItsStore(): WorkspaceEntityStorage {
  return if (this is WorkspaceEntityStorageBuilderImpl) this.toStorage() else this
}

internal fun createAttachment(path: String, bytes: ByteArray, displayText: String): Attachment {
  val attachment = Attachment(path, bytes, displayText)
  attachment.isIncluded = true
  return attachment
}

internal fun WorkspaceEntityStorage.serializeTo(stream: OutputStream) {
  val serializer = EntityStorageSerializerImpl(SimpleEntityTypesResolver, VirtualFileUrlManagerImpl())
  serializer.serializeCache(stream, this.makeSureItsStore())
}

internal fun WorkspaceEntityStorageBuilderImpl.serializeDiff(stream: OutputStream) {
  val serializer = EntityStorageSerializerImpl(SimpleEntityTypesResolver, VirtualFileUrlManagerImpl())
  serializer.serializeDiffLog(stream, this.changeLog)
}
