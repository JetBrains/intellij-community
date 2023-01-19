// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.TimeoutUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.Promise
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

private val LOG = Logger.getInstance(IdeActivity::class.java)

internal enum class IdeActivityState { NOT_STARTED, STARTED, FINISHED }

@Deprecated("Use com.intellij.internal.statistic.StructuredIdeActivity instead. " +
            "It allows us to generate events scheme from the product code and ensures that only data matching the scheme is being sent.")
@ApiStatus.Internal
class IdeActivity @JvmOverloads constructor(private val projectOrNullForApplication: Project?,
                                            private val group: String,
                                            private val activityName: String? = null) {
  private val id = counter.incrementAndGet()

  private var state = IdeActivityState.NOT_STARTED
  private var startedTimestamp = 0L

  private fun createDataWithActivityId(): FeatureUsageData {
    return FeatureUsageData().addData("ide_activity_id", id)
  }

  fun started(): IdeActivity {
    return startedWithData(Consumer { })
  }

  private fun startedWithData(consumer: Consumer<FeatureUsageData>): IdeActivity {
    if (!LOG.assertTrue(state == IdeActivityState.NOT_STARTED, state.name)) return this
    state = IdeActivityState.STARTED

    val data = createDataWithActivityId()
    consumer.accept(data)

    startedTimestamp = System.nanoTime()
    FUCounterUsageLogger.getInstance().logEvent(projectOrNullForApplication, group, appendActivityName(STARTED_EVENT_ID), data)
    return this
  }

  fun startedWithDataAsync(dataSupplier: (FeatureUsageData) -> Promise<FeatureUsageData>): IdeActivity {
    if (!LOG.assertTrue(state == IdeActivityState.NOT_STARTED, state.name)) return this
    state = IdeActivityState.STARTED
    startedTimestamp = System.nanoTime()

    dataSupplier(createDataWithActivityId()).then { data ->
      FUCounterUsageLogger.getInstance().logEvent(projectOrNullForApplication, group, appendActivityName(STARTED_EVENT_ID), data)
    }
    return this
  }

  fun stageStarted(stageName: String): IdeActivity {
    if (!LOG.assertTrue(state == IdeActivityState.STARTED, state.name)) return this

    FUCounterUsageLogger.getInstance().logEvent(projectOrNullForApplication, group, appendActivityName(stageName), createDataWithActivityId())
    return this
  }

  fun stageStarted(stageClass: Class<*>): IdeActivity {
    if (!LOG.assertTrue(state == IdeActivityState.STARTED, state.name)) return this

    val data = createDataWithActivityId().addData("stage_class", stageClass.name)
    FUCounterUsageLogger.getInstance().logEvent(projectOrNullForApplication, group, appendActivityName("stage"), data)
    return this
  }

  fun finished(): IdeActivity = finished(null)

  fun finished(consumer: Consumer<FeatureUsageData>?): IdeActivity {
    if (!LOG.assertTrue(state == IdeActivityState.STARTED, state.name)) return this
    state = IdeActivityState.FINISHED

    val data = createDataWithActivityId()
    consumer?.accept(data)

    val duration = TimeoutUtil.getDurationMillis(startedTimestamp)
    FUCounterUsageLogger.getInstance().logEvent(projectOrNullForApplication, group, appendActivityName("finished"),
                                                data.addData("duration_ms", duration))
    return this
  }

  private fun appendActivityName(state: String): String {
    if (activityName == null) return state
    return "$activityName.$state"
  }

  companion object {
    private val counter = AtomicInteger(0)
    const val STARTED_EVENT_ID = "started"

    @JvmStatic
    @JvmOverloads
    fun started(projectOrNullForApplication: Project?, group: @NonNls String, activityName: @NonNls String? = null): IdeActivity =
      IdeActivity(projectOrNullForApplication, group, activityName).started()
  }
}