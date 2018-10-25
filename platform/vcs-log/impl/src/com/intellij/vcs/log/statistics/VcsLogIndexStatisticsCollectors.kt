// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.statistics

import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getBooleanUsage
import com.intellij.internal.statistic.utils.getCountingUsage
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
  override fun getUsages(): MutableSet<UsageDescriptor> {
    val usages = mutableSetOf<UsageDescriptor>()

    if (!Registry.`is`("vcs.log.index.git")) {
      usages.add(getBooleanUsage("index.disabled.in.registry", true))
    }

    if (Registry.`is`("vcs.log.index.force")) {
      usages.add(getBooleanUsage("index.forced.in.registry", true))
    }

    getBigRepositoriesList()?.let { bigRepositoriesList ->
      if (bigRepositoriesList.repositoriesCount > 0) {
        usages.add(getCountingUsage("big.repositories", bigRepositoriesList.repositoriesCount))
      }
    }

    return usages
  }

  private fun getBigRepositoriesList() = getServiceIfCreated<VcsLogBigRepositoriesList>(VcsLogBigRepositoriesList::class.java)

  override fun getContext(): FUSUsageContext = FUSUsageContext.OS_CONTEXT

  override fun getGroupId(): String = "statistics.vcs.log.index.application"
}

class VcsLogIndexProjectStatisticsCollector : ProjectUsagesCollector() {
  override fun getUsages(project: Project): MutableSet<UsageDescriptor> {
    val usages = mutableSetOf<UsageDescriptor>()

    getIndexCollector(project)?.state?.let { indexCollectorState ->
      usages.add(getCountingUsage("indexing.too.long.notification", indexCollectorState.indexingTooLongNotification))
      usages.add(getCountingUsage("resume.indexing.click", indexCollectorState.resumeClick))
      val indexingTime = TimeUnit.MILLISECONDS.toMinutes(indexCollectorState.indexTime).toInt()
      usages.add(getCountingUsage("indexing.time.minutes", indexingTime))
    }

    getSharedSettings(project)?.let { sharedSettings ->
      if (!sharedSettings.isIndexSwitchedOn) {
        usages.add(getBooleanUsage("index.disabled.in.project", true))
      }
    }

    return usages
  }

  private fun getSharedSettings(project: Project) = getServiceIfCreated<VcsLogSharedSettings>(project, VcsLogSharedSettings::class.java)

  private fun getIndexCollector(project: Project) = getServiceIfCreated<VcsLogIndexCollector>(project, VcsLogIndexCollector::class.java)

  override fun getContext(project: Project): FUSUsageContext = FUSUsageContext.OS_CONTEXT

  override fun getGroupId(): String = "statistics.vcs.log.index.project"
}

class VcsLogIndexCollectorState {
  var indexingTooLongNotification: Int = 0
  var resumeClick: Int = 0
  var indexTime: Long = 0

  fun copy(): VcsLogIndexCollectorState {
    val copy = VcsLogIndexCollectorState()
    copy.indexingTooLongNotification = indexingTooLongNotification
    copy.resumeClick = resumeClick
    copy.indexTime = indexTime
    return copy
  }
}

@State(name = "VcsLogIndexCollector",
       storages = [Storage(value = StoragePathMacros.CACHE_FILE)])
class VcsLogIndexCollector : PersistentStateComponent<VcsLogIndexCollectorState> {
  private val lock = Any()
  private lateinit var state: VcsLogIndexCollectorState

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

  fun reportIndexingTooLongNotification() {
    synchronized(lock) {
      state.indexingTooLongNotification++
    }
  }

  fun reportResumeClick() {
    synchronized(lock) {
      state.resumeClick++
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
