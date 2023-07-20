package com.intellij.cce.workspace.storages

import com.intellij.cce.core.Session
import java.io.File

interface FeaturesStorage : StorageWithMetadata {
  companion object {
    val EMPTY: FeaturesStorage = object : FeaturesStorage {
      override fun saveSession(session: Session, filePath: String) = session.clearFeatures()
      override fun saveMetadata() = Unit
      override fun getSessions(filePath: String) = emptyList<String>()
      override fun getFeatures(session: String, filePath: String) = ""
      override val metadataFile: File = File("")
    }
  }

  fun saveSession(session: Session, filePath: String)
  fun getSessions(filePath: String): List<String>
  fun getFeatures(session: String, filePath: String): String
}
