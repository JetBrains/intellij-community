// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.storages

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.cce.workspace.SessionSerializer
import com.intellij.cce.workspace.info.FileSessionsInfo

open class SessionsStorage(storageDir: String) : StorageWithMetadataBase(storageDir) {
  companion object {
    private val gson = Gson()
    private val sessionSerializer = SessionSerializer()
  }

  private var sessionFiles: MutableMap<String, String> = mutableMapOf()
  private val keyValueStorage = FileArchivesStorage(filesDir.toString())

  open fun saveSessions(sessionsInfo: FileSessionsInfo) {
    val json = sessionSerializer.serialize(sessionsInfo)
    val archivePath = keyValueStorage.save("${toFileName(sessionsInfo.filePath)}.json", json)
    sessionFiles[sessionsInfo.filePath] = archivePath
  }

  override fun saveMetadata() {
    val filesJson = gson.toJson(sessionFiles)
    metadataFile.writeText(filesJson)
  }

  open fun getSessionFiles(): List<Pair<String, String>> {
    if (!metadataFile.exists()) return emptyList()
    val json = metadataFile.readText()
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