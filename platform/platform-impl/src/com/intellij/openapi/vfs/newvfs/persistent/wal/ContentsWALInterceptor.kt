// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.wal

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ContentsInterceptor
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendLines

class ContentsWALInterceptor(
  private val contentsStoragePath: Path
) : ContentsInterceptor {
  var count = 0
  private val LOG = Logger.getInstance(ContentsWALInterceptor::class.java)

  init {
    LOG.warn(contentsStoragePath.absolutePathString())
    FileUtil.createIfNotExists(contentsStoragePath.toFile())
  }

  override fun onWriteBytes(underlying: (record: Int, bytes: ByteArraySequence, fixedSize: Boolean) -> Unit): (record: Int, bytes: ByteArraySequence, fixedSize: Boolean) -> Unit = {record, bytes, fixedSize ->
    LOG.info("${++count}")
    contentsStoragePath.appendLines(listOf(
      "$record: ${if (fixedSize) "(fixed size)" else ""} len ${bytes.length}"
    ))
    underlying(record, bytes, fixedSize)
  }
}