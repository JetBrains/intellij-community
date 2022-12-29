// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ContentsInterceptor

class ContentsLogInterceptor(
  private val processor: OperationProcessor
) : ContentsInterceptor {

  override fun onWriteBytes(underlying: (record: Int, bytes: ByteArraySequence, fixedSize: Boolean) -> Unit) =
    { record: Int, bytes: ByteArraySequence, fixedSize: Boolean ->
      //contentsStoragePath.appendLines(listOf(
      //  "$record: ${if (fixedSize) "(fixed size)" else ""} len ${bytes.length}"
      //))
      underlying(record, bytes, fixedSize)
    }
}