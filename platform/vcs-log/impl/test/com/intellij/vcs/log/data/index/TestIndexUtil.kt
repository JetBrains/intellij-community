// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.VcsLogProgress
import com.intellij.vcs.log.data.VcsLogStorageImpl
import com.intellij.vcs.log.impl.FatalErrorHandler

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
  val errorConsumer = TestErrorConsumer()

  val storage = VcsLogStorageImpl(project, providersMap, errorConsumer, disposable)
  return VcsLogPersistentIndex(project, storage, VcsLogProgress(disposable), providersMap, errorConsumer, disposable)
}

class TestErrorConsumer : FatalErrorHandler {
  override fun displayFatalErrorMessage(message: String) {
    HeavyPlatformTestCase.fail(message)
  }

  override fun consume(source: Any?, throwable: Throwable) {
    HeavyPlatformTestCase.fail(throwable.message)
  }
}