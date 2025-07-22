// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.VcsLogData
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<PostponableLogRefresher>()

@ApiStatus.Internal
class PostponableLogRefresher internal constructor(private val logData: VcsLogData) {
  private val rootsToRefresh = mutableSetOf<VirtualFile>()
  private val refreshers = mutableMapOf<String, Refresher>()

  init {
    logData.addDataPackChangeListener(DataPackChangeListener { dataPack ->
      for (refresher in refreshers.values) {
        refresher.setDataPack(dataPack)
      }
    })
  }

  fun registerRefresher(disposable: Disposable, id: String, refresher: Refresher) {
    require(refreshers.putIfAbsent(id, refresher) == null) {
      "Refresher with id $id is already registered"
    }
    refresherActivated(refresher, true)
    Disposer.register(disposable) {
      refreshers.remove(id)
    }
  }

  fun refresherActivated(id: String) {
    val refresher = refreshers[id]
    if (refresher != null) {
      refresherActivated(refresher, false)
    }
  }

  private fun refresherActivated(refresher: Refresher, firstTime: Boolean) {
    logData.initialize()

    if (!rootsToRefresh.isEmpty()) {
      refreshPostponedRoots()
    }
    else {
      refresher.validate(firstTime)
    }
  }

  @RequiresEdt
  fun refresh(root: VirtualFile, postponed: Boolean) {
    if (postponed) {
      LOG.debug("Postponed refresh for $root")
      rootsToRefresh.add(root)
    }
    else {
      logData.refresh(setOf(root), true)
    }
  }

  @RequiresEdt
  fun hasPostponedRoots(): Boolean {
    return !rootsToRefresh.isEmpty()
  }

  @RequiresEdt
  fun refreshPostponedRoots() {
    val toRefresh = rootsToRefresh.toSet()
    rootsToRefresh.removeAll(toRefresh) // clear the set, but keep roots which could possibly arrive after collecting them in the var.
    logData.refresh(toRefresh)
  }

  interface Refresher {
    fun setDataPack(dataPack: DataPack)
    fun validate(refresh: Boolean)
  }
}
