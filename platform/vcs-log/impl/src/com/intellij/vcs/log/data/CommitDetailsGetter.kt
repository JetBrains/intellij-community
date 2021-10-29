// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.index.VcsLogIndex

/**
 * The CommitDetailsGetter is responsible for getting [complete commit details][VcsFullCommitDetails] from the cache or from the VCS.
 */
class CommitDetailsGetter internal constructor(storage: VcsLogStorage,
                                               logProviders: Map<VirtualFile, VcsLogProvider>,
                                               index: VcsLogIndex,
                                               parentDisposable: Disposable) :
  AbstractDataGetterWithSequentialLoader<VcsFullCommitDetails>(storage, logProviders, index, parentDisposable) {

  init {
    LowMemoryWatcher.register({ clear() }, this)
  }

  override fun getFromAdditionalCache(commit: Int): VcsFullCommitDetails? = null

  @Throws(VcsException::class)
  override fun doLoadCommitsDataFromProvider(logProvider: VcsLogProvider,
                                             root: VirtualFile,
                                             hashes: List<String>,
                                             consumer: Consumer<in VcsFullCommitDetails>) {
    logProvider.readFullDetails(root, hashes, consumer)
  }
}