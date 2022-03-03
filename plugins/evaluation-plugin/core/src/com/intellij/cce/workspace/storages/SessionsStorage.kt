package com.intellij.cce.workspace.storages

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.cce.workspace.SessionSerializer
import com.intellij.cce.workspace.info.FileSessionsInfo
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Paths

open class SessionsStorage(val storageDir: String) {
  companion object {
    private const val pathsListFile = "files.json"
    private val gson = Gson()
    private val sessionSerializer = SessionSerializer()
  }

  private var filesCounter = 0
  private var sessionFiles: MutableMap<String, String> = mutableMapOf()
  private val filesDir = Paths.get(storageDir, "files")
  private val keyValueStorage = FileArchivesStorage(filesDir.toString())

  fun saveSessions(sessionsInfo: FileSessionsInfo) {
    val json = sessionSerializer.serialize(sessionsInfo)
    val archivePath = keyValueStorage.save("${Paths.get(sessionsInfo.filePath).fileName}($filesCounter).json", json)
    sessionFiles[sessionsInfo.filePath] = archivePath
    filesCounter++
  }

  fun saveEvaluationInfo() {
    val filesJson = gson.toJson(sessionFiles)
    FileWriter(Paths.get(storageDir, pathsListFile).toString()).use { it.write(filesJson) }
  }

  fun getSessionFiles(): List<Pair<String, String>> {
    val sessionsListFile = Paths.get(storageDir, pathsListFile).toFile()
    if (!sessionsListFile.exists()) return emptyList()
    val json = FileReader(sessionsListFile).use { it.readText() }
    val type = object : TypeToken<MutableMap<String, String>>() {}.type
    sessionFiles = gson.fromJson(json, type)
    for (path in sessionFiles.keys) sessionFiles[path] = sessionFiles[path]!!
    return sessionFiles.entries.map { it.toPair() }
  }

  open fun getSessions(path: String): FileSessionsInfo {
    val sessionsPath = sessionFiles[path] ?: throw NoSuchElementException()
    return sessionSerializer.deserialize(keyValueStorage.get(sessionsPath))
  }
}