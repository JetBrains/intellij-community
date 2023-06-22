// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.util.io.DataEnumerator
import kotlinx.coroutines.CoroutineScope

interface VfsLogContext {
  val operationLogStorage: OperationLogStorage
  val payloadStorage: PayloadStorage
  val stringEnumerator: DataEnumerator<String>
  val coroutineScope: CoroutineScope

  fun enqueueOperationWrite(tag: VfsOperationTag, compute: VfsLogContext.() -> VfsOperation<*>): Unit =
    operationLogStorage.enqueueOperationWrite(coroutineScope, tag) { compute() }

  fun enumerateAttribute(attribute: FileAttribute): EnumeratedFileAttribute =
    EnumeratedFileAttribute(stringEnumerator.enumerate(attribute.id), attribute.version, attribute.isFixedSize)

  fun deenumerateAttribute(enumeratedAttr: EnumeratedFileAttribute): FileAttribute? {
    return FileAttribute.instantiateForRecovery(
      stringEnumerator.valueOf(enumeratedAttr.enumeratedId) ?: return null,
      enumeratedAttr.version,
      enumeratedAttr.fixedSize
    ) // attribute.shouldEnumerate is not used yet
  }
}