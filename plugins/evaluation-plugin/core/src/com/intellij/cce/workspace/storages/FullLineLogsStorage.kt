package com.intellij.cce.workspace.storages

import com.google.gson.Gson
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
    properties.setProperty("flcc_search_logging_enabled", "true")
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
}
