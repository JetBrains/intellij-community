// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.wal

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.util.AttributesInterceptor
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendLines

class AttributesWALInterceptor(
  private val attributesStoragePath: Path
): AttributesInterceptor {
  private val LOG = Logger.getInstance(AttributesWALInterceptor::class.java)

  init {
    LOG.warn(attributesStoragePath.absolutePathString())
    FileUtil.createIfNotExists(attributesStoragePath.toFile())
  }

  override fun onWriteAttribute(underlying: AttributeOutputStream, fileId: Int, attribute: FileAttribute): AttributeOutputStream =
    object : AttributeOutputStream(underlying) {
      override fun writeEnumeratedString(str: String?) = underlying.writeEnumeratedString(str)

      override fun close() {
        interceptClose()
        super.close()
      }

      private fun interceptClose() {
        attributesStoragePath.appendLines(listOf(
          "$fileId ${attribute.id.hashCode()} ${underlying.getResultingBuffer().length}"
        ))
      }
    }

  override fun onDeleteAttributes(fileId: Int) {
    attributesStoragePath.appendLines(listOf(
      "-$fileId"
    ))
  }
}