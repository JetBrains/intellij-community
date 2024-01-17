// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.openapi.diagnostic.fileLogger
import java.nio.file.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

@OptIn(ExperimentalPathApi::class)
fun ZipOutputStream.addFolder(entryName: String, folder: Path) {
  for (path in folder.walk()) {
    val relativized = folder.relativize(path)
    val prefix = if (entryName.isNotEmpty()) {
      "$entryName/"
    } else ""
    addEntry(prefix + relativized.joinToString(separator = "/") { it.name }) {
      path.inputStream().use {
        it.copyTo(this)
      }
    }
  }
}

fun ZipOutputStream.addFile(entryName: String, byteArray: ByteArray) {
  addEntry(entryName) { write(byteArray) }
}

fun ZipOutputStream.addEntry(entryName: String, operation: () -> Unit) {
  val zipEntry = ZipEntry(entryName)
  try {
    putNextEntry(zipEntry)
    operation()
  }
  catch (ex: Exception) {
    fileLogger().error("Failed to add file to stream", ex)
  }
  finally {
    closeEntry()
  }
}