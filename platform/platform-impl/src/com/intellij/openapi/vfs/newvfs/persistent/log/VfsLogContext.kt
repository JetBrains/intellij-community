// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.util.io.DataEnumerator
import kotlinx.coroutines.CoroutineScope

interface VfsLogContext : CoroutineScope {
  val descriptorStorage: DescriptorStorage
  val payloadStorage: PayloadStorage
  val stringEnumerator: DataEnumerator<String>

  fun enqueueDescriptorWrite(tag: VfsOperationTag, compute: VfsLogContext.() -> VfsOperation<*>): Unit =
    descriptorStorage.enqueueDescriptorWrite(this, tag) { compute() }
}