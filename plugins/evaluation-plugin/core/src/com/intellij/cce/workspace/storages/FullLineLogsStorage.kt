// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.storages

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.nio.file.Paths

class FullLineLogsStorage(storageDir: String) : StorageWithMetadataBase(storageDir) {
  companion object {
    private val gson = Gson()
  }

  private var logFiles: MutableMap<String, String> = mutableMapOf()
  private val keyValueStorage = FileArchivesStorage(filesDir.toString())

  fun enableLogging(path: String) {
    val logPath = "${toFileName(path)}.jsonl"
    val properties = System.getProperties()
    properties.setProperty("flcc_search_log_path", Paths.get(storageDir, logPath).toString())
    logFiles[path] = logPath
  }

  override fun saveMetadata() {
    val toRemove = mutableSetOf<String>()
    for ((fileKey, path) in logFiles.entries) {
      val file = Paths.get(storageDir, path).toFile()
      if (!file.exists()) {
        toRemove.add(fileKey)
        continue
      }
      val content = file.readText()
      logFiles[fileKey] = keyValueStorage.save(path, content)
      file.delete()
    }
    toRemove.forEach { logFiles.remove(it) }
    val filesJson = gson.toJson(logFiles)
    metadataFile.writeText(filesJson)
  }

  fun getLog(path: String): String? {
    if (!metadataFile.exists()) return null
    val json = metadataFile.readText()
    val type = object : TypeToken<MutableMap<String, String>>() {}.type
    logFiles = gson.fromJson(json, type)
    val logFile = logFiles[path] ?: return null
    return keyValueStorage.get(logFile)
  }
}
