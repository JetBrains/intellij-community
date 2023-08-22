// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.storages

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPOutputStream

fun File.compress() {
  if (!isDirectory) throw IllegalStateException("Can't compress $path. It's not a directory.")
  if (!exists()) throw IllegalStateException("Can't compress $path. It doesn't exist.")

  val output = Files.createFile(Paths.get("$path.tar.gz")).toFile()
  TarArchiveOutputStream(GZIPOutputStream(BufferedOutputStream(FileOutputStream(output)))).use {
    it.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_STAR)
    it.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU)
    addFilesToCompression(it, this, ".")
  }
  deleteRecursively()
}

private fun addFilesToCompression(out: TarArchiveOutputStream, file: File, dir: String) {
  val entry = dir + File.separator + file.name
  if (file.isFile) {
    out.putArchiveEntry(TarArchiveEntry(file, entry))
    BufferedInputStream(FileInputStream(file)).use { it.copyTo(out) }
    out.closeArchiveEntry()
  }
  else if (file.isDirectory) {
    val children = file.listFiles()
    if (children != null) {
      for (child in children) {
        addFilesToCompression(out, child, entry)
      }
    }
  }
}