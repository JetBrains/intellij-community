package com.intellij.cce.evaluation

import com.intellij.cce.workspace.filter.SessionsFilter
import com.intellij.cce.workspace.info.FileSessionsInfo
import com.intellij.cce.workspace.storages.SessionsStorage

class FilteredSessionsStorage(private val filter: SessionsFilter, storage: SessionsStorage) : SessionsStorage(storage.storageDir) {
  override fun getSessions(path: String): FileSessionsInfo {
    val sessionsInfo = super.getSessions(path)
    val filteredSessions = filter.apply(sessionsInfo.sessions)
    return FileSessionsInfo(sessionsInfo.filePath, sessionsInfo.text, filteredSessions)
  }
}