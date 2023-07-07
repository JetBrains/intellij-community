// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.eventLog.events.EventFields.Long
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector


class WorkspaceModelFusLogger : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = Util.GROUP

  object Util {
    val GROUP = EventLogGroup("workspaceModel", 1)

    /**
     * Time of loading of iml files. These files are loaded to the empty storage on start. The loading happens every time, but
     *   if we load from cache, asynchronously.
     */
    private val LOAD_JPS_FROM_IML = GROUP.registerEvent("jps.loading_iml", Long("duration_ms"))

    /** Log if the cache was used during project loading. */
    private val LOAD_FROM_CACHE = GROUP.registerEvent("cache.used", Boolean("from_cache"))

    /** Load time of workspace model loading. */
    private val LOAD_CACHE_TIME = GROUP.registerEvent("cache.loading", Long("duration_ms"))

    /** Load time of workspace model saving. */
    private val SAVE_CACHE_TIME = GROUP.registerEvent("cache.saving", Long("duration_ms"))

    /** Logs size of the workspace model cache in bytes. */
    private val CACHE_SIZE = GROUP.registerEvent("cache.size", Long("size_bytes"))

    fun logLoadingJpsFromIml(durationMs: Long) {
      LOAD_JPS_FROM_IML.log(durationMs)
    }

    fun logIsLoadedFromCache(loadedFromCache: Boolean) {
      LOAD_FROM_CACHE.log(loadedFromCache)
    }

    fun logCacheLoading(durationMs: Long) {
      LOAD_CACHE_TIME.log(durationMs)
    }

    fun logCacheSaving(durationMs: Long) {
      SAVE_CACHE_TIME.log(durationMs)
    }

    fun logCacheSize(size: Long) {
      CACHE_SIZE.log(size)
    }
  }
}
