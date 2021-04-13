// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.TimeoutUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import java.util.concurrent.atomic.AtomicInteger

private val LOG = Logger.getInstance(StructuredIdeActivity::class.java)

@ApiStatus.Internal
class StructuredIdeActivity constructor(private val projectOrNullForApplication: Project?,
                                        private val ideActivityDefinition: IdeActivityDefinition) {
  private val id = counter.incrementAndGet()

  private var state = IdeActivityState.NOT_STARTED
  private var startedTimestamp = 0L

  @JvmOverloads
  fun started(dataSupplier: (() -> List<EventPair<*>>)? = null): StructuredIdeActivity {
    if (!LOG.assertTrue(state == IdeActivityState.NOT_STARTED, state.name)) return this
    state = IdeActivityState.STARTED

    val data: MutableList<EventPair<*>> = mutableListOf(IdeActivityDefinition.activityId.with(id))
    if (dataSupplier != null) {
      data.addAll(dataSupplier())
    }

    startedTimestamp = System.nanoTime()
    ideActivityDefinition.started.log(projectOrNullForApplication, data)
    return this
  }

  fun startedAsync(dataSupplier: () -> Promise<List<EventPair<*>>>): StructuredIdeActivity {
    if (!LOG.assertTrue(state == IdeActivityState.NOT_STARTED, state.name)) return this
    state = IdeActivityState.STARTED
    startedTimestamp = System.nanoTime()

    val data: MutableList<EventPair<*>> = mutableListOf(IdeActivityDefinition.activityId.with(id))

    dataSupplier().then { additionalData ->
      data.addAll(additionalData)
      ideActivityDefinition.started.log(projectOrNullForApplication, data)
    }

    return this
  }

  @JvmOverloads
  fun stageStarted(stage: VarargEventId, dataSupplier: (() -> List<EventPair<*>>)? = null): StructuredIdeActivity {
    if (!LOG.assertTrue(state == IdeActivityState.STARTED, state.name)) return this

    val data: MutableList<EventPair<*>> = mutableListOf(IdeActivityDefinition.activityId.with(id))
    if (dataSupplier != null) {
      data.addAll(dataSupplier())
    }

    stage.log(projectOrNullForApplication, data)
    return this
  }

  @JvmOverloads
  fun finished(dataSupplier: (() -> List<EventPair<*>>)? = null): StructuredIdeActivity {
    if (!LOG.assertTrue(state == IdeActivityState.STARTED, state.name)) return this
    state = IdeActivityState.FINISHED

    val data: MutableList<EventPair<*>> = mutableListOf(IdeActivityDefinition.activityId.with(id))
    if (dataSupplier != null) {
      data.addAll(dataSupplier())
    }

    data.add(EventFields.DurationMs.with(TimeoutUtil.getDurationMillis(startedTimestamp)))
    ideActivityDefinition.finished.log(projectOrNullForApplication, data)
    return this
  }

  companion object {
    private val counter = AtomicInteger(0)
  }
}

class IdeActivityDefinition constructor(val group: EventLogGroup,
                                        val activityName: String?,
                                        startEventAdditionalFields: Array<EventField<*>> = emptyArray(),
                                        finishEventAdditionalFields: Array<EventField<*>> = emptyArray()) {
  val started = group.registerVarargEvent(appendActivityName(activityName, "started"), activityId, *startEventAdditionalFields)
  val finished = group.registerVarargEvent(appendActivityName(activityName, "finished"), activityId, EventFields.DurationMs,
                                           *finishEventAdditionalFields)

  private fun appendActivityName(activityName: String?, state: String): String {
    if (activityName == null) return state
    return "$activityName.$state"
  }

  @JvmOverloads
  fun registerStage(stageName: String, additionalFields: Array<EventField<*>> = emptyArray()): VarargEventId = group.registerVarargEvent(
    appendActivityName(activityName, stageName), activityId, *additionalFields)

  companion object {
    val activityId = EventFields.Int("ide_activity_id")
  }
}