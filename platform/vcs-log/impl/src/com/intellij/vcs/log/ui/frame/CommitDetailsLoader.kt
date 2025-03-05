// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.util.Consumer
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.DataGetter
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

private typealias HashedCommitId = Int

/**
 * A common commit selection intended to be shared between multiple selection subscribers
 */
internal class CommitDetailsLoader<D : VcsCommitMetadata> @JvmOverloads constructor(
  private val commitDetailsGetter: DataGetter<D>,
  parentDisposable: Disposable,
  private val limit: Int? = null,
) {
  private val listeners = CopyOnWriteArrayList<Listener<in D>>()

  private var lastRequest: ProgressIndicator? = null

  init {
    Disposer.register(parentDisposable) {
      lastRequest?.cancel()
      lastRequest = null
    }
  }

  @RequiresEdt
  fun loadDetails(commitIds: List<HashedCommitId>) {
    lastRequest?.cancel()
    lastRequest = null
    val commitIds = commitIds.subList(0, (limit?.coerceAtMost(commitIds.size) ?: commitIds.size))
    if (commitIds.isEmpty()) {
      listeners.forEach { it.onLoadingStopped() }
      listeners.forEach { it.onEmptySelection() }
    }
    else {
      listeners.forEach { it.onSelection() }
      listeners.forEach { it.onLoadingStarted() }

      val indicator = EmptyProgressIndicator()
      lastRequest = indicator

      commitDetailsGetter.loadCommitsData(commitIds, Consumer { detailsList: List<D> ->
        if (lastRequest === indicator && !indicator.isCanceled) {
          if (commitIds.size != detailsList.size) {
            LOG.error("Loaded incorrect number of details $detailsList for commits $commitIds")
          }
          lastRequest = null
          listeners.forEach { it.onDetailsLoaded(commitIds, detailsList) }
          listeners.forEach { it.onLoadingStopped() }
        }
      }, Consumer { t: Throwable ->
        if (lastRequest === indicator && !indicator.isCanceled) {
          lastRequest = null
          LOG.error("Error loading details for commits $commitIds", t)
          listeners.forEach { it.onError(t) }
          listeners.forEach { it.onLoadingStopped() }
        }
      }, indicator)
    }
  }

  fun addListener(listener: Listener<in D>) {
    listeners.add(listener)
  }

  fun removeListener(listener: Listener<in D>) {
    listeners.remove(listener)
  }

  interface Listener<D : VcsCommitMetadata> : EventListener {
    fun onLoadingStarted() {}
    fun onLoadingStopped() {}
    fun onEmptySelection() {}
    fun onSelection() {}
    fun onDetailsLoaded(commitIds: List<HashedCommitId>, details: List<D>) {}
    fun onError(error: Throwable) {}
  }

  companion object {
    private val LOG = Logger.getInstance(CommitDetailsLoader::class.java)
  }
}