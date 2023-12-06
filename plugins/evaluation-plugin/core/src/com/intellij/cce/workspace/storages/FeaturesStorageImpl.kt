// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.storages

import com.google.gson.Gson
import com.intellij.cce.core.Session
import com.intellij.cce.workspace.FeaturesSerializer
import java.io.File
import java.nio.file.Paths

class FeaturesStorageImpl(storageDir: String) : StorageWithMetadataBase(storageDir), FeaturesStorage {
  companion object {
    private val gson: Gson = Gson()
  }

  private val fileFeaturesStorages: MutableMap<String, FileArchivesStorage> = mutableMapOf()
  private val fileMapping: MutableMap<String, String> = mutableMapOf()
  private val featuresInfo: FeaturesInfo = FeaturesInfo()

  init {
    if (metadataFile.exists()) {
      val json = metadataFile.readText()
      val result = gson.fromJson(json, FilesFeaturesInfo::class.java)
      featuresInfo.addAllFeatures(result.features)
      fileFeaturesStorages.putAll(result.files.mapValues {
        FileArchivesStorage(Paths.get(storageDir, result.files[it.key]).toString())
      })
    }
  }

  override fun saveSession(session: Session, filePath: String) {
    if (filePath !in fileFeaturesStorages) {
      addFileFeaturesStorage(filePath)
    }
    fileFeaturesStorages[filePath]?.save(session.id, FeaturesSerializer.serialize(session.getFeatures()))
    session.clearFeatures()
  }

  override fun saveMetadata() {
    val filesFeaturesJson = gson.toJson(FilesFeaturesInfo(fileMapping, featuresInfo))
    metadataFile.writeText(filesFeaturesJson)
  }

  override fun getSessions(filePath: String): List<String> {
    val storage = fileFeaturesStorages[filePath] ?: return emptyList()
    return storage.getKeys().map { File(it).name.removeSuffix(storage.fileExtension) }
  }

  override fun getFeatures(session: String, filePath: String): String {
    val storage = fileFeaturesStorages[filePath] ?: return "{}"
    return storage.get("$session${storage.fileExtension}")
  }

  private fun addFileFeaturesStorage(filePath: String) {
    val targetDir = toFileName(filePath)
    fileMapping[filePath] = targetDir
    fileFeaturesStorages[filePath] = FileArchivesStorage(Paths.get(storageDir, targetDir).toString())
  }

  private class FeaturesInfo {
    val element: Set<String> = mutableSetOf()
    val context: Set<String> = mutableSetOf()
    val user: Set<String> = mutableSetOf()
    val session: Set<String> = mutableSetOf()

    fun addElementFeatures(features: Set<String>) = element.addFeatures(features)
    fun addContextFeatures(features: Set<String>) = context.addFeatures(features)
    fun addUserFeatures(features: Set<String>) = user.addFeatures(features)
    fun addSessionFeatures(features: Set<String>) = session.addFeatures(features)

    fun addAllFeatures(features: FeaturesInfo) {
      addElementFeatures(features.element)
      addContextFeatures(features.context)
      addUserFeatures(features.user)
      addSessionFeatures(features.session)
    }

    private fun Set<String>.addFeatures(features: Set<String>) {
      val set = this as MutableSet<String>
      set.addAll(features)
    }
  }

  private data class FilesFeaturesInfo(val files: Map<String, String>,
                                       val features: FeaturesInfo
  )
}