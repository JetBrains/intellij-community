// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.util

import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.util.io.storage.RefCountingContentStorage

object InterceptorInjection {
  fun injectInContents(storage: RefCountingContentStorage, interceptors: List<ContentsInterceptor>): RefCountingContentStorage =
    object : RefCountingContentStorage by storage {
      override fun writeBytes(record: Int, bytes: ByteArraySequence, fixedSize: Boolean) {
        interceptors.forEach { it.onWriteBytes(record, bytes, fixedSize) }
        storage.writeBytes(record, bytes, fixedSize)
      }
    }
}