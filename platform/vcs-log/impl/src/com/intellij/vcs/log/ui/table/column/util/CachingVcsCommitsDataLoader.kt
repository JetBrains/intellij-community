// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.table.column.util

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.data.util.VcsCommitsDataLoader
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CachingVcsCommitsDataLoader<T>(
  private val loader: VcsCommitsDataLoader<T>,
  cacheSize: Long = 150
) : VcsCommitsDataLoader<T> {

  private val cache = Caffeine.newBuilder().maximumSize(cacheSize).build<CommitId, T>()

  init {
    Disposer.register(this, loader)
  }

  override fun loadData(commits: List<CommitId>, onChange: (Map<CommitId, T>) -> Unit) {
    loader.loadData(commits) {
      cache.putAll(it)
      onChange(it)
    }
  }

  @RequiresEdt
  fun getData(commit: CommitId): T? = cache.getIfPresent(commit)

  override fun dispose() {
    cache.invalidateAll()
  }
}