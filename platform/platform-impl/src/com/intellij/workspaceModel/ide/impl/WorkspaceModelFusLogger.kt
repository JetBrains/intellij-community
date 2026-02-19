// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields.DurationMs
import com.intellij.internal.statistic.eventLog.events.EventFields.Long
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object WorkspaceModelFusLogger : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  val GROUP = EventLogGroup("workspace.model", 3)

  /**
   * Time of loading of iml files. These files are loaded to the empty storage on start. The loading happens every time, but
   *   if we load from cache, asynchronously.
   */
  private val LOAD_JPS_FROM_IML = GROUP.registerEvent("jps.iml.loaded", DurationMs)

  /** Load time of workspace model cache loading. -1 if the cache was not loaded */
  private val LOAD_CACHE_TIME = GROUP.registerEvent("cache.loaded", DurationMs)

  /** Logs size of the workspace model cache in bytes and time of saving. Size is -1 is the cache is not saved */
  private val CACHE_SAVED = GROUP.registerEvent("cache.saved", DurationMs, Long("size_bytes"))

  fun logLoadingJpsFromIml(durationMs: Long) {
    LOAD_JPS_FROM_IML.log(durationMs)
  }

  fun logCacheLoading(durationMs: Long) {
    LOAD_CACHE_TIME.log(durationMs)
  }

  fun logCacheSave(time: Long, size: Long) {
    CACHE_SAVED.log(time, size)
  }
}
