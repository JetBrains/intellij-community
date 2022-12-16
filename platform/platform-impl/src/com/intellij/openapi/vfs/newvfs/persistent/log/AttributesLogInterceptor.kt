// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.newvfs.AttributeOutputStream
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSConnection
import com.intellij.openapi.vfs.newvfs.persistent.intercept.AttributesInterceptor
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.appendLines

class AttributesLogInterceptor(
  private val attributesStoragePath: Path
): AttributesInterceptor {
  private val LOG = Logger.getInstance(AttributesLogInterceptor::class.java)

  init {
    LOG.warn(attributesStoragePath.absolutePathString())
    FileUtil.createIfNotExists(attributesStoragePath.toFile())
  }

  override fun onWriteAttribute(underlying: (connection: PersistentFSConnection, fileId: Int, attribute: FileAttribute) -> AttributeOutputStream): (connection: PersistentFSConnection, fileId: Int, attribute: FileAttribute) -> AttributeOutputStream = { connection, fileId, attribute ->
    val aos = underlying(connection, fileId, attribute)
    object : AttributeOutputStream(aos) {
      override fun writeEnumeratedString(str: String?) = aos.writeEnumeratedString(str)

      override fun close() {
        interceptClose()
        super.close()
      }

      private fun interceptClose() {
        attributesStoragePath.appendLines(listOf(
          "$fileId ${attribute.id.hashCode()} ${aos.getResultingBuffer().length}"
        ))
      }
    }
  }

  override fun onDeleteAttributes(underlying: (connection: PersistentFSConnection, fileId: Int) -> Unit): (connection: PersistentFSConnection, fileId: Int) -> Unit = {connection, fileId ->
    attributesStoragePath.appendLines(listOf(
      "-$fileId"
    ))
    underlying(connection, fileId)
  }
}