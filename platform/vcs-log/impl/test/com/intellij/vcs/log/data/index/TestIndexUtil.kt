// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.VcsLogProgress
import com.intellij.vcs.log.data.VcsLogStorageImpl
import com.intellij.vcs.log.impl.VcsLogErrorHandler

private fun VcsLogPersistentIndex.doIndex(full: Boolean) {
  indexNow(full)
}

fun VcsLogPersistentIndex.index(root: VirtualFile, commits: Set<Int>) {
  for (commit in commits) {
    markForIndexing(commit, root)
  }
  doIndex(true)
}

fun setUpIndex(project: Project, root: VirtualFile, logProvider: VcsLogProvider, disposable: Disposable): VcsLogPersistentIndex {
  val providersMap = mapOf(root to logProvider)
  val errorConsumer = FailingErrorHandler()

  val storage = VcsLogStorageImpl(project, providersMap, errorConsumer, disposable)
  return VcsLogPersistentIndex.create(project, storage, providersMap, VcsLogProgress(disposable), errorConsumer, disposable)!!
}

class FailingErrorHandler : VcsLogErrorHandler {
  override fun handleError(source: VcsLogErrorHandler.Source, throwable: Throwable) = HeavyPlatformTestCase.fail(throwable.message)
  override fun displayMessage(message: String) = HeavyPlatformTestCase.fail(message)
}