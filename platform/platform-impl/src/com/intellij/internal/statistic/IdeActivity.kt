// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.constraints.isDisposed
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.util.SingleAlarm
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

private val LOG = Logger.getInstance("#com.intellij.internal.statistic.IdeActivity")

private enum class State { NOT_STARTED, STARTED, FINISHED }

class IdeActivity @JvmOverloads constructor(private val projectOrNullForApplication: Project?,
                                            private val group: String,
                                            private val activityName: String? = null) {
  private val id = counter.incrementAndGet()

  private var state = State.NOT_STARTED

  private fun createDataWithActivityId(): FeatureUsageData {
    return FeatureUsageData().addData("ide_activity_id", id)
  }

  fun started(): IdeActivity {
    return startedWithData(Consumer { })
  }

  fun startedWithData(consumer: Consumer<FeatureUsageData>): IdeActivity {
    LOG.assertTrue(state == State.NOT_STARTED, state.name)
    state = State.STARTED

    val data = createDataWithActivityId().addProject(projectOrNullForApplication)
    consumer.accept(data)

    FUCounterUsageLogger.getInstance().logEvent(group, appendActivityName("started"), data)
    return this
  }

  fun stageStarted(stageName: String): IdeActivity {
    LOG.assertTrue(state == State.STARTED, state.name)
    FUCounterUsageLogger.getInstance().logEvent(group, appendActivityName(stageName), createDataWithActivityId())
    return this
  }

  fun stageStarted(stageClass: Class<*>): IdeActivity {
    LOG.assertTrue(state == State.STARTED, state.name)
    val data = createDataWithActivityId().addData("stage_class", stageClass.name)
    FUCounterUsageLogger.getInstance().logEvent(group, appendActivityName("stage"), data)
    return this
  }

  fun finished(): IdeActivity {
    LOG.assertTrue(state == State.STARTED, state.name)
    state = State.FINISHED
    FUCounterUsageLogger.getInstance().logEvent(group, appendActivityName("finished"), createDataWithActivityId())
    return this
  }

  private fun appendActivityName(state: String): String {
    if (activityName == null) return state
    return "$activityName.$state"
  }

  companion object {
    private val counter = AtomicInteger(0)

    @JvmStatic
    @JvmOverloads
    fun started(projectOrNullForApplication: Project?, group: String, activityName: String? = null): IdeActivity =
      IdeActivity(projectOrNullForApplication, group, activityName).started()
  }
}

class DelayedIdeActivity @JvmOverloads constructor(val group: String, val activityName: String? = null) {
  private val disposable = Disposer.newDisposable()
  private var activity: IdeActivity? = null

  private var state = State.NOT_STARTED;

  fun started() : DelayedIdeActivity {
    synchronized(this) {
      LOG.assertTrue(state == State.NOT_STARTED, state.name)
      state = State.STARTED;

      if (FeatureUsageLogger.getConfig().isRecordEnabled()) { // avoid unnecessary work, when disabled
        val delay = 1000

        val application = ApplicationManager.getApplication()
        application.runReadAction {
          if (application.isDisposed) return@runReadAction

          Disposer.register(application, disposable)
          SingleAlarm(Runnable { delayedStart(delay) }, delay, Alarm.ThreadToUse.POOLED_THREAD, disposable).request()
        }
      }
    }
    return this
  }

  private fun delayedStart(@Suppress("SameParameterValue") delay: Int) {
    synchronized(this) {
      if (disposable.isDisposed) return
      activity = IdeActivity(null, group, activityName).startedWithData(Consumer { data ->
        data.addData("activity_start_delay", delay)
      })
    }
  }

  fun finished() : DelayedIdeActivity{
    synchronized(this) {
      LOG.assertTrue(state == State.STARTED, state.name)
      state = State.FINISHED;

      Disposer.dispose(disposable)
      activity?.finished()
    }
    return this
  }
}
