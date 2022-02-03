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
import com.intellij.util.containers.SmartHashSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

private val LOG = Logger.getInstance(StructuredIdeActivity::class.java)

/**
 * New style API to record information about process.
 * It allows you to record start/finish events, calculate duration and link them by common id.
 *
 * To record a process:
 * - Register ide activity in event log group (see [com.intellij.internal.statistic.eventLog.EventLogGroup.registerIdeActivity]).
 * - Use [com.intellij.internal.statistic.IdeActivityDefinition.started] or
 * [com.intellij.internal.statistic.IdeActivityDefinition.startedAsync] to record start event and
 * [com.intellij.internal.statistic.StructuredIdeActivity.finished] to record finish event.
 *
 * See example in dev-guide/fus-collectors.md.
 */
@ApiStatus.Internal
class StructuredIdeActivity internal constructor(private val projectOrNullForApplication: Project?,
                                                 private val ideActivityDefinition: IdeActivityDefinition,
                                                 private val parentActivity: StructuredIdeActivity? = null) {
  private val id: Int = parentActivity?.id ?: counter.incrementAndGet()

  private var state = IdeActivityState.NOT_STARTED
  private var startedTimestamp = 0L

  private val innerActivities: MutableSet<StructuredIdeActivity> = Collections.synchronizedSet(SmartHashSet())

  @JvmOverloads
  fun started(dataSupplier: (() -> List<EventPair<*>>)? = null): StructuredIdeActivity {
    if (parentNotStarted(parentActivity)) return this
    if (!LOG.assertTrue(state == IdeActivityState.NOT_STARTED, state.name)) return this

    state = IdeActivityState.STARTED
    parentActivity?.addInnerActivity(this)

    val data: MutableList<EventPair<*>> = mutableListOf(IdeActivityDefinition.activityId.with(id))
    if (dataSupplier != null) {
      data.addAll(dataSupplier())
    }

    startedTimestamp = System.nanoTime()
    ideActivityDefinition.started.log(projectOrNullForApplication, data)
    return this
  }

  fun startedAsync(dataSupplier: () -> Promise<List<EventPair<*>>>): StructuredIdeActivity {
    if (parentNotStarted(parentActivity)) return this
    if (!LOG.assertTrue(state == IdeActivityState.NOT_STARTED, state.name)) return this

    state = IdeActivityState.STARTED
    parentActivity?.addInnerActivity(this)
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
    if (parentNotStarted(parentActivity)) return this
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
    if (parentNotStarted(parentActivity)) return this
    if (!LOG.assertTrue(state == IdeActivityState.STARTED, state.name)) return this

    state = IdeActivityState.FINISHED
    parentActivity?.removeInnerActivity(this)
    innerActivities.toList().forEach { it.finished() }

    val data: MutableList<EventPair<*>> = mutableListOf(IdeActivityDefinition.activityId.with(id))
    if (dataSupplier != null) {
      data.addAll(dataSupplier())
    }

    data.add(EventFields.DurationMs.with(TimeoutUtil.getDurationMillis(startedTimestamp)))
    ideActivityDefinition.finished.log(projectOrNullForApplication, data)
    return this
  }

  private fun addInnerActivity(inner: StructuredIdeActivity) {
    innerActivities.add(inner)
  }

  private fun removeInnerActivity(inner: StructuredIdeActivity) {
    innerActivities.remove(inner)
  }

  private fun parentNotStarted(parentActivity: StructuredIdeActivity?): Boolean {
    if (parentActivity != null) {
      if (!LOG.assertTrue(parentActivity.state == IdeActivityState.STARTED, parentActivity.state.name)) return true
    }
    return false
  }

  companion object {
    private val counter = AtomicInteger(0)
  }
}

class IdeActivityDefinition internal constructor(val group: EventLogGroup,
                                                 private val parentActivityDefinition: IdeActivityDefinition? = null,
                                                 internal var activityName: String?,
                                                 startEventAdditionalFields: Array<EventField<*>> = emptyArray(),
                                                 finishEventAdditionalFields: Array<EventField<*>> = emptyArray()) {
  val started = group.registerVarargEvent(appendActivityName(activityName, "started"), activityId, *startEventAdditionalFields)
  val finished = group.registerVarargEvent(appendActivityName(activityName, "finished"), activityId, EventFields.DurationMs,
                                           *finishEventAdditionalFields)

  @JvmOverloads
  fun started(project: Project?, dataSupplier: (() -> List<EventPair<*>>)? = null): StructuredIdeActivity {
    logIfShouldBeStartedWithParent()
    return StructuredIdeActivity(project, this).started(dataSupplier)
  }

  @JvmOverloads
  fun startedWithParent(project: Project?,
                        parentActivity: StructuredIdeActivity,
                        dataSupplier: (() -> List<EventPair<*>>)? = null): StructuredIdeActivity {
    logIfParentNotRegistered()
    return StructuredIdeActivity(project, this, parentActivity).started(dataSupplier)
  }

  fun startedAsync(project: Project?, dataSupplier: () -> Promise<List<EventPair<*>>>): StructuredIdeActivity {
    logIfShouldBeStartedWithParent()
    return StructuredIdeActivity(project, this).startedAsync(dataSupplier)
  }

  @JvmOverloads
  fun startedAsyncWithParent(project: Project?,
                             parentActivity: StructuredIdeActivity,
                             dataSupplier: (() -> List<EventPair<*>>)? = null): StructuredIdeActivity {
    logIfParentNotRegistered()
    return StructuredIdeActivity(project, this, parentActivity).started(dataSupplier)
  }

  private fun logIfShouldBeStartedWithParent() {
    LOG.assertTrue(parentActivityDefinition == null, "Use startedWithParent/startedAsyncWithParent")
  }

  private fun logIfParentNotRegistered() {
    LOG.assertTrue(parentActivityDefinition != null, "Use started/startedAsync or register parent activity")
  }

  private fun appendActivityName(activityName: String?, state: String): String {
    if (activityName == null) return state
    val name = if (parentActivityDefinition == null) activityName else "${parentActivityDefinition.activityName}.$activityName"
    return "$name.$state"
  }

  @JvmOverloads
  fun registerStage(stageName: String, additionalFields: Array<EventField<*>> = emptyArray()): VarargEventId = group.registerVarargEvent(
    appendActivityName(activityName, stageName), activityId, *additionalFields)

  companion object {
    val activityId = EventFields.Int("ide_activity_id")
  }
}