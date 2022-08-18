package com.intellij.cce.workspace.storages

import com.intellij.cce.core.Session

interface FeaturesStorage {
  companion object {
    val EMPTY: FeaturesStorage = object : FeaturesStorage {
      override fun saveSession(session: Session, filePath: String) = session.clearFeatures()
      override fun saveFeaturesInfo() = Unit
      override fun getSessions(filePath: String) = emptyList<String>()
      override fun getFeatures(session: String, filePath: String) = ""
    }
  }

  fun saveSession(session: Session, filePath: String)
  fun saveFeaturesInfo()
  fun getSessions(filePath: String): List<String>
  fun getFeatures(session: String, filePath: String): String
}