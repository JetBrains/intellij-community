// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.index.IndexedDetails.Companion.createMetadata
import com.intellij.vcs.log.data.index.VcsLogIndex
import it.unimi.dsi.fastutil.ints.IntConsumer
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet

class MiniDetailsGetter internal constructor(project: Project,
                                             storage: VcsLogStorage,
                                             logProviders: Map<VirtualFile, VcsLogProvider>,
                                             private val topCommitsDetailsCache: TopCommitsCache,
                                             index: VcsLogIndex,
                                             parentDisposable: Disposable) :
  AbstractDataGetter<VcsCommitMetadata>(storage, logProviders, index, parentDisposable) {

  private val factory = project.getService(VcsLogObjectsFactory::class.java)

  @RequiresBackgroundThread
  @Throws(VcsException::class)
  fun loadCommitsData(hashes: Iterable<Int>,
                      consumer: Consumer<in VcsCommitMetadata>,
                      indicator: ProgressIndicator) {
    val toLoad = IntOpenHashSet()
    for (id in hashes) {
      val details = getFromCache(id)
      if (details == null || details is LoadingDetails) {
        toLoad.add(id)
      }
      else {
        consumer.consume(details)
      }
    }
    if (!toLoad.isEmpty()) {
      indicator.checkCanceled()
      preLoadCommitData(toLoad, consumer)
      notifyLoaded()
    }
  }

  override fun getFromAdditionalCache(commitId: Int): VcsCommitMetadata? {
    return topCommitsDetailsCache[commitId]
  }

  @Throws(VcsException::class)
  override fun readDetails(logProvider: VcsLogProvider,
                           root: VirtualFile,
                           hashes: List<String>,
                           consumer: Consumer<in VcsCommitMetadata>) {
    logProvider.readMetadata(root, hashes, consumer)
  }

  @Throws(VcsException::class)
  override fun preLoadCommitData(commits: IntSet, consumer: Consumer<in VcsCommitMetadata>) {
    val dataGetter = index.dataGetter
    if (dataGetter == null) {
      super.preLoadCommitData(commits, consumer)
      return
    }
    val notIndexed = IntOpenHashSet()
    commits.forEach(IntConsumer { commit: Int ->
      val metadata = createMetadata(commit, dataGetter, storage, factory)
      if (metadata == null) {
        notIndexed.add(commit)
      }
      else {
        saveInCache(commit, metadata)
        consumer.consume(metadata)
      }
    })
    if (!notIndexed.isEmpty()) {
      super.preLoadCommitData(notIndexed, consumer)
    }
  }
}