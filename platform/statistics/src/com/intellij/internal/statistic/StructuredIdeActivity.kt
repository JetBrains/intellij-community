// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.TimeoutUtil
import com.intellij.util.containers.SmartHashSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

private val LOG = logger<StructuredIdeActivity>()

/**
 * New style API to record information about processes.
 * It allows you to record start/finish events, link them by common id, and calculate duration.
 *
 * To record a process:
 * - Register ide activity in an event log group (see [EventLogGroup.registerIdeActivity]).
 * - Use [IdeActivityDefinition.started] or
 * [IdeActivityDefinition.startedAsync] to record start events,
 * and [finished] to record finish events.
 *
 * See example in [FUS Collectors](https://youtrack.jetbrains.com/articles/IJPL-A-153/Fus-Collectors).
 */
@ApiStatus.Internal
class StructuredIdeActivity internal constructor(
  private val projectOrNullForApplication: Project?,
  internal val ideActivityDefinition: IdeActivityDefinition,
  private val parentActivity: StructuredIdeActivity? = null,
) {
  val id: Int = parentActivity?.id ?: counter.incrementAndGet()
  private val stepsCounter: AtomicInteger by lazy { AtomicInteger(0) }
  val stepId: Int by lazy {
    if (parentActivity == null) return@lazy -1 // fail safe
    var rootParentActivity: StructuredIdeActivity = parentActivity
    while (rootParentActivity.parentActivity != null) {
      rootParentActivity = rootParentActivity.parentActivity
    }
    rootParentActivity.stepsCounter.incrementAndGet()
  }

  private var state = IdeActivityState.NOT_STARTED
  var startedTimestamp: Long = 0L
    private set

  private val innerActivities: MutableSet<StructuredIdeActivity> = Collections.synchronizedSet(SmartHashSet())

  @JvmOverloads
  fun started(dataSupplier: (() -> List<EventPair<*>>)? = null): StructuredIdeActivity {
    if (parentNotStarted(parentActivity)) return this
    if (!LOG.assertTrue(state == IdeActivityState.NOT_STARTED, state.name)) return this

    state = IdeActivityState.STARTED
    parentActivity?.addInnerActivity(this)

    val data: MutableList<EventPair<*>> = createDataWithIDs()
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

    val data: MutableList<EventPair<*>> = createDataWithIDs()

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

    val data: MutableList<EventPair<*>> = createDataWithIDs()
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

    val data: MutableList<EventPair<*>> = createDataWithIDs()
    if (dataSupplier != null) {
      data.addAll(dataSupplier())
    }

    data.add(EventFields.DurationMs.with(TimeoutUtil.getDurationMillis(startedTimestamp)))
    ideActivityDefinition.finished.log(projectOrNullForApplication, data)
    return this
  }

  private fun createDataWithIDs(): MutableList<EventPair<*>> {
    return listOfNotNull(
      IdeActivityDefinition.activityId.with(id),
      if (ideActivityDefinition.subStepWithStepId && parentActivity != null) IdeActivityDefinition.stepId.with(stepId) else null
    ).toMutableList()
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
                                                               finishEventAdditionalFields: Array<EventField<*>> = emptyArray(),
                                                               internal val subStepWithStepId: Boolean = false) {
  val started: VarargEventId = group.registerVarargEvent(appendActivityName(activityName, "started"), *notNull(activityId, stepIdField(), *startEventAdditionalFields))
  val finished: VarargEventId = group.registerVarargEvent(appendActivityName(activityName, "finished"), *notNull(activityId, stepIdField(), EventFields.DurationMs, *finishEventAdditionalFields))

  @JvmOverloads
  fun started(project: Project?, dataSupplier: (() -> List<EventPair<*>>)? = null): StructuredIdeActivity {
    logIfShouldBeStartedWithParent()
    return StructuredIdeActivity(project, this).started(dataSupplier)
  }

  @JvmOverloads
  fun startedWithParent(project: Project?,
                        parentActivity: StructuredIdeActivity,
                        dataSupplier: (() -> List<EventPair<*>>)? = null): StructuredIdeActivity {
    logIfParentNotRegistered(parentActivity)
    return StructuredIdeActivity(project, this, parentActivity).started(dataSupplier)
  }

  fun startedAsync(project: Project?, dataSupplier: () -> Promise<List<EventPair<*>>>): StructuredIdeActivity {
    logIfShouldBeStartedWithParent()
    return StructuredIdeActivity(project, this).startedAsync(dataSupplier)
  }

  fun startedAsyncWithParent(project: Project?, parentActivity: StructuredIdeActivity, dataSupplier: () -> Promise<List<EventPair<*>>>): StructuredIdeActivity {
    logIfParentNotRegistered(parentActivity)
    return StructuredIdeActivity(project, this, parentActivity).startedAsync(dataSupplier)
  }

  private fun logIfShouldBeStartedWithParent() {
    LOG.assertTrue(parentActivityDefinition == null, "Use startedWithParent/startedAsyncWithParent")
  }

  private fun logIfParentNotRegistered(parentActivity: StructuredIdeActivity) {
    if (parentActivity.ideActivityDefinition == this) return // use self as parent is allowed
    LOG.assertTrue(parentActivityDefinition != null, "Use started/startedAsync or register parent activity")
  }

  private fun appendActivityName(activityName: String?, state: String): String {
    if (activityName == null) return state
    val name = if (parentActivityDefinition == null) activityName else "${parentActivityDefinition.activityName}.$activityName"
    return "$name.$state"
  }

  @JvmOverloads
  fun registerStage(stageName: String, additionalFields: Array<EventField<*>> = emptyArray()): VarargEventId =
    group.registerVarargEvent(appendActivityName(activityName, stageName), *notNull(activityId, stepIdField(), *additionalFields))

  private fun notNull(vararg fields: EventField<*>?): Array<EventField<*>> {
    return fields.filterNotNull().toTypedArray()
  }

  private fun stepIdField(): EventField<Int>? {
    return if (subStepWithStepId) stepId else null
  }

  companion object {
    val activityId: IntEventField = EventFields.Int("ide_activity_id")
    val stepId: IntEventField = EventFields.Int("step_id")
  }
}
