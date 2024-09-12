// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.storages

import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class FileArchivesStorage(private val storageDir: String) : KeyValueStorage<String, String> {
  val fileExtension: String = ".gz"

  init {
    ensureDirExists(storageDir)
  }

  override fun get(key: String): String {
    val path = Paths.get(storageDir, key).toString()
    return InputStreamReader(GZIPInputStream(FileInputStream(path))).use {
      it.readText()
    }
  }

  override fun getKeys(): List<String> {
    return File(storageDir).listFiles()?.map { it.name } ?: emptyList()
  }

  override fun save(baseKey: String, value: String): String {
    val output = Files.createFile(Paths.get(storageDir, "$baseKey$fileExtension")).toFile()
    OutputStreamWriter(GZIPOutputStream(FileOutputStream(output)), StandardCharsets.UTF_8).use {
      it.write(value)
    }
    return output.name
  }
}