// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newCounterMetric
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager.getService
import com.intellij.openapi.components.ServiceManager.getServiceIfCreated
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.log.data.index.VcsLogBigRepositoriesList
import com.intellij.vcs.log.impl.VcsLogSharedSettings
import java.util.concurrent.TimeUnit

class VcsLogIndexApplicationStatisticsCollector : ApplicationUsagesCollector() {
  override fun getMetrics(): MutableSet<MetricEvent> {
    val metricEvents = mutableSetOf<MetricEvent>()
    if (!Registry.`is`("vcs.log.index.git")) {
      metricEvents.add(newMetric("index.disabled.in.registry", true))
    }

    if (Registry.`is`("vcs.log.index.force")) {
      metricEvents.add(newMetric("index.forced.in.registry", true))
    }

    getBigRepositoriesList()?.let { bigRepositoriesList ->
      if (bigRepositoriesList.repositoriesCount > 0) {
        metricEvents.add(newCounterMetric("big.repositories", bigRepositoriesList.repositoriesCount))
      }
    }

    return metricEvents
  }

  private fun getBigRepositoriesList() = getServiceIfCreated<VcsLogBigRepositoriesList>(VcsLogBigRepositoriesList::class.java)

  override fun getGroupId(): String = "vcs.log.index.application"

  override fun getVersion(): Int = 2
}

class VcsLogIndexProjectStatisticsCollector : ProjectUsagesCollector() {
  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    val usages = mutableSetOf<MetricEvent>()

    getIndexCollector(project)?.state?.let { indexCollectorState ->
      val indexingTime = TimeUnit.MILLISECONDS.toMinutes(indexCollectorState.indexTime).toInt()
      usages.add(newCounterMetric("indexing.time.minutes", indexingTime))
    }

    getSharedSettings(project)?.let { sharedSettings ->
      if (!sharedSettings.isIndexSwitchedOn) {
        usages.add(newMetric("index.disabled.in.project", true))
      }
    }

    return usages
  }

  private fun getSharedSettings(project: Project) = getServiceIfCreated<VcsLogSharedSettings>(project, VcsLogSharedSettings::class.java)

  private fun getIndexCollector(project: Project) = getServiceIfCreated<VcsLogIndexCollector>(project, VcsLogIndexCollector::class.java)

  override fun getGroupId(): String = "vcs.log.index.project"

  override fun getVersion(): Int = 2
}

class VcsLogIndexCollectorState {
  var indexTime: Long = 0

  fun copy(): VcsLogIndexCollectorState {
    val copy = VcsLogIndexCollectorState()
    copy.indexTime = indexTime
    return copy
  }
}

@State(name = "VcsLogIndexCollector",
       storages = [Storage(value = StoragePathMacros.CACHE_FILE)])
class VcsLogIndexCollector : PersistentStateComponent<VcsLogIndexCollectorState> {
  private val lock = Any()
  private var state: VcsLogIndexCollectorState

  init {
    synchronized(lock) {
      state = VcsLogIndexCollectorState()
    }
  }

  override fun getState(): VcsLogIndexCollectorState? {
    synchronized(lock) {
      return state.copy()
    }
  }

  override fun loadState(state: VcsLogIndexCollectorState) {
    synchronized(lock) {
      this.state = state
    }
  }

  fun reportIndexingTime(time: Long) {
    synchronized(lock) {
      state.indexTime += time
    }
  }

  fun reportFreshIndex() {
    synchronized(lock) {
      state.indexTime = 0
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): VcsLogIndexCollector = getService(project, VcsLogIndexCollector::class.java)
  }
}
