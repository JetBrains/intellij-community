// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table.column.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.data.util.VcsCommitsDataLoader
import kotlinx.coroutines.FlowPreview
import java.util.EventListener

/**
 * Loads and supplies the data for a "window" of commits passed to [requestData] method
 * to avoid pollution the memory with the data for all commits
 */
@OptIn(FlowPreview::class)
internal class WindowedVcsCommitsDataModel<T : Any>(
  private val loader: VcsCommitsDataLoader<T>,
) : Disposable {
  private var isDisposed = false
  private val dataStore = mutableMapOf<CommitId, Ref<T>>()

  private val listeners = EventDispatcher.create(Listener::class.java)

  init {
    Disposer.register(this, loader)
  }

  @RequiresEdt
  fun requestData(commits: List<CommitId>) {
    if (isDisposed) return
    dataStore.keys.retainAll(commits.toSet())
    for (commit in commits) {
      dataStore.computeIfAbsent(commit) { Ref(null) }
    }
    val toLoad = dataStore.entries.mapNotNull { (commit, dataHolder) ->
      commit.takeIf { dataHolder.get() == null }
    }
    if (toLoad.isEmpty()) return
    loader.loadData(toLoad) {
      if (isDisposed) return@loadData
      val commits = buildList {
        for ((commit, data) in it) {
          dataStore[commit]?.set(data)
          add(commit)
        }
      }
      listeners.multicaster.onDataChanged(commits)
    }
  }

  @RequiresEdt
  fun getData(commit: CommitId): T? {
    return dataStore[commit]?.get()
  }

  fun addListener(listener: Listener) {
    if (isDisposed) return
    listeners.addListener(listener)
  }

  fun removeListener(listener: Listener) {
    listeners.removeListener(listener)
  }

  override fun dispose() {
    isDisposed = true
    dataStore.clear()
    listeners.listeners.forEach {
      listeners.removeListener(it)
    }
  }

  interface Listener : EventListener {
    fun onDataChanged(commits: List<CommitId>)
  }
}
