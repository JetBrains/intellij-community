package com.intellij.completion.ml.local.models.storage

import com.google.gson.Gson
import com.intellij.util.io.*
import java.nio.file.Path
import kotlin.io.path.writeText

object StorageUtil {
  private val GSON by lazy { Gson() }

  private const val STORAGE_INFO_FILE = "info.json"

  fun readInfo(storageDirectory: Path): StorageInfo? {
    val infoFile = storageDirectory.resolve(STORAGE_INFO_FILE)
    if (!infoFile.exists()) return null

    return GSON.fromJson(infoFile.readText(), StorageInfo::class.java)
  }

  fun saveInfo(info: StorageInfo, storageDirectory: Path) {
    val infoFile = storageDirectory.resolve(STORAGE_INFO_FILE)
    infoFile.writeText(GSON.toJson(info))
  }

  fun<K> clearMap(map: PersistentHashMap<K, *>) {
    val existing = HashSet<K>()
    map.processKeysWithExistingMapping {
      existing.add(it)
      true
    }
    existing.forEach { fileId ->
      map.remove(fileId)
    }
  }

  fun prepareStorage(storageDirectory: Path, version: Int): Boolean {
    var isValid = false
    if (storageDirectory.exists()) {
      val info = readInfo(storageDirectory)
      if (info == null || !info.isValid || info.version != version) {
        storageDirectory.delete()
      } else {
        isValid = true
      }
    }
    storageDirectory.createDirectories()
    return isValid
  }
}