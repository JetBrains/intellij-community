// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.TimeoutUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

private val LOG = Logger.getInstance("#com.intellij.internal.statistic.IdeActivity")

private enum class State { NOT_STARTED, STARTED, FINISHED }

@ApiStatus.Internal
class IdeActivity @JvmOverloads constructor(private val projectOrNullForApplication: Project?,
                                            private val group: String,
                                            private val activityName: String? = null) {
  private val id = counter.incrementAndGet()

  private var state = State.NOT_STARTED
  private var startedTimestamp = 0L

  private fun createDataWithActivityId(): FeatureUsageData {
    return FeatureUsageData().addData("ide_activity_id", id)
  }

  fun started(): IdeActivity {
    return startedWithData(Consumer { })
  }

  fun startedWithData(consumer: Consumer<FeatureUsageData>): IdeActivity {
    if (!LOG.assertTrue(state == State.NOT_STARTED, state.name)) return this
    state = State.STARTED

    val data = createDataWithActivityId().addProject(projectOrNullForApplication)
    consumer.accept(data)

    startedTimestamp = System.nanoTime()
    FUCounterUsageLogger.getInstance().logEvent(group, appendActivityName(STARTED_EVENT_ID), data)
    return this
  }

  fun stageStarted(stageName: String): IdeActivity {
    if (!LOG.assertTrue(state == State.STARTED, state.name)) return this

    FUCounterUsageLogger.getInstance().logEvent(group, appendActivityName(stageName), createDataWithActivityId())
    return this
  }

  fun stageStarted(stageClass: Class<*>): IdeActivity {
    if (!LOG.assertTrue(state == State.STARTED, state.name)) return this

    val data = createDataWithActivityId().addData("stage_class", stageClass.name)
    FUCounterUsageLogger.getInstance().logEvent(group, appendActivityName("stage"), data)
    return this
  }

  fun finished(): IdeActivity {
    if (!LOG.assertTrue(state == State.STARTED, state.name)) return this
    state = State.FINISHED

    val duration = TimeoutUtil.getDurationMillis(startedTimestamp)
    FUCounterUsageLogger.getInstance().logEvent(group, appendActivityName("finished"),
                                                createDataWithActivityId().addData("duration_ms", duration))
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
    fun started(projectOrNullForApplication: Project?, group: String, activityName: String? = null): IdeActivity =
      IdeActivity(projectOrNullForApplication, group, activityName).started()
  }
}