// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ContentsInterceptor
import java.nio.file.Path

class ContentsLogInterceptor(
  private val contentsStoragePath: Path
) : ContentsInterceptor {

  init {
    //LOG.warn(contentsStoragePath.absolutePathString())
    FileUtil.createIfNotExists(contentsStoragePath.toFile())
  }

  override fun onWriteBytes(underlying: (record: Int, bytes: ByteArraySequence, fixedSize: Boolean) -> Unit) =
    { record: Int, bytes: ByteArraySequence, fixedSize: Boolean ->
      //contentsStoragePath.appendLines(listOf(
      //  "$record: ${if (fixedSize) "(fixed size)" else ""} len ${bytes.length}"
      //))
      underlying(record, bytes, fixedSize)
    }
}