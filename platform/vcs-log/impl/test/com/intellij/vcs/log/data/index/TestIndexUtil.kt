// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.VcsLogProgress
import com.intellij.vcs.log.data.VcsLogStorageImpl
import com.intellij.vcs.log.impl.VcsLogErrorHandler
import com.intellij.vcs.log.util.PersistentUtil

private fun VcsLogPersistentIndex.doIndex(full: Boolean) {
  indexNow(full)
}

fun VcsLogPersistentIndex.index(root: VirtualFile, commits: Set<Int>) {
  for (commit in commits) {
    markForIndexing(commit, root)
  }
  doIndex(true)
}

fun setUpIndex(project: Project,
               root: VirtualFile,
               logProvider: VcsLogProvider,
               useSqlite: Boolean,
               disposable: Disposable): VcsLogPersistentIndex {
  val providersMap = mapOf(root to logProvider)
  val errorConsumer = FailingErrorHandler()

  val logId = PersistentUtil.calcLogId(project, providersMap)

  val storage = if (useSqlite) SqliteVcsLogStorageBackend(project, logId, providersMap, errorConsumer, disposable)
  else VcsLogStorageImpl(project, logId, providersMap, errorConsumer, disposable)

  val backend = if (storage is VcsLogStorageBackend) storage
  else PhmVcsLogStorageBackend.create(project, storage, setOf(root), logId, errorConsumer, disposable)!!

  val indexers = VcsLogPersistentIndex.getAvailableIndexers(providersMap)
  return VcsLogPersistentIndex(project, providersMap, indexers, storage, backend, VcsLogProgress(disposable), errorConsumer, disposable)
}

private class FailingErrorHandler : VcsLogErrorHandler {
  override fun handleError(source: VcsLogErrorHandler.Source, throwable: Throwable) {
    throw throwable
  }

  override fun displayMessage(message: String) {
    throw RuntimeException(message)
  }
}