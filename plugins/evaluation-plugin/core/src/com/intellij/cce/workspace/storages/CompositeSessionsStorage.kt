// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.storages

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.cce.workspace.info.FileSessionsInfo
import java.nio.file.Paths

class CompositeSessionsStorage(storageDir: String) : SessionsStorage(storageDir) {
  private val storages = mutableMapOf<String, SessionsStorage>()

  override fun saveSessions(sessionsInfo: FileSessionsInfo) {
    val projectName = sessionsInfo.projectName
    val storage = storages.getOrPut(projectName) { SessionsStorage(Paths.get(storageDir, projectName).toString()) }
    storage.saveSessions(sessionsInfo)
  }

  override fun saveMetadata() {
    storages.forEach { (_, storage) -> storage.saveMetadata() }
    val json = gson.toJson(storages.keys.toList())
    metadataFile.writeText(json)
  }

  override fun getSessionFiles(): List<Pair<String, String>> {
    val json = metadataFile.readText()
    val type = object : TypeToken<ArrayList<String>>() {}.type
    val storagesList: List<String> = gson.fromJson(json, type)
    storagesList.forEach { storages[it] = SessionsStorage(Paths.get(storageDir, it).toString()) }
    val result = mutableListOf<Pair<String, String>>()
    for ((projectName, storage) in storages) {
      result.addAll(storage.getSessionFiles().map { "$projectName:${it.first}" to it.second })
    }
    return result
  }

  override fun getSessions(path: String): FileSessionsInfo {
    val (projectName, filePath) = path.split(":", limit = 2)
    return storages[projectName]!!.getSessions(filePath)
  }
}

private val gson = Gson()
