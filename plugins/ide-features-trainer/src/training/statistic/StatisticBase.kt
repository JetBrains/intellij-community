// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.TimeoutUtil
import training.learn.interfaces.Lesson
import training.learn.interfaces.Module
import training.statistic.FeatureUsageStatisticConsts.DURATION
import training.statistic.FeatureUsageStatisticConsts.GROUP_COLLAPSED
import training.statistic.FeatureUsageStatisticConsts.GROUP_EVENT
import training.statistic.FeatureUsageStatisticConsts.GROUP_EXPANDED
import training.statistic.FeatureUsageStatisticConsts.GROUP_NAME
import training.statistic.FeatureUsageStatisticConsts.GROUP_STATE
import training.statistic.FeatureUsageStatisticConsts.LANGUAGE
import training.statistic.FeatureUsageStatisticConsts.LESSON_ID
import training.statistic.FeatureUsageStatisticConsts.MODULE_NAME
import training.statistic.FeatureUsageStatisticConsts.PASSED
import training.statistic.FeatureUsageStatisticConsts.PROGRESS_PERCENTAGE
import training.statistic.FeatureUsageStatisticConsts.START
import training.statistic.FeatureUsageStatisticConsts.START_MODULE_ACTION
import java.util.concurrent.ConcurrentHashMap

@Suppress("PropertyName")
class StatisticBase {

  private val sessionLessonTimestamp: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
  //should be the same as res/META-INF/plugin.xml <statistics.counterUsagesCollector groupId="ideFeaturesTrainer" .../>
  private val GROUP_ID = "ideFeaturesTrainer"

  companion object {
    val instance: StatisticBase by lazy { ApplicationManager.getApplication().getService(StatisticBase::class.java) }
    internal val LOG = logger<StatisticBase>()
  }

  fun onStartLesson(lesson: Lesson) {
    sessionLessonTimestamp[lesson.id] = System.nanoTime()
    logEvent(START, FeatureUsageData()
      .addData(LESSON_ID, lesson.id)
      .addData(PROGRESS_PERCENTAGE, calcProgressPercentage(lesson.module))
      .addData(LANGUAGE, lesson.lang.toLowerCase()))
  }

  fun onPassLesson(lesson: Lesson) {
    val timestamp = sessionLessonTimestamp[lesson.id]
    if (timestamp == null) {
      LOG.warn("Unable to find timestamp for a lesson: ${lesson.name}")
      return
    }
    val delta = TimeoutUtil.getDurationMillis(timestamp)
    logEvent(PASSED, FeatureUsageData()
      .addData(LESSON_ID, lesson.id)
      .addData(LANGUAGE, lesson.lang.toLowerCase())
      .addData(PROGRESS_PERCENTAGE, calcProgressPercentage(lesson.module))
      .addData(DURATION, delta))
  }

  private fun logEvent(event: String, featureUsageData: FeatureUsageData) {
    FUCounterUsageLogger.getInstance().logEvent(GROUP_ID, event, featureUsageData)
  }

  /**
   * @state if the state has changed to expanded = true
   */
  fun onGroupStateChanged(groupName: String, state: Boolean) {
    val expandedCollapsed = if (state) GROUP_EXPANDED else GROUP_COLLAPSED
    val whiteListedGroupName = try {
      GroupNames.valueOf(groupName.toUpperCase())
    }
    catch (e: IllegalArgumentException) {
      LOG.warn("Unable to parse group name for collision/expanding event.")
      return
    }
    logEvent(GROUP_EVENT, FeatureUsageData()
      .addData(GROUP_NAME, whiteListedGroupName.name)
      .addData(GROUP_STATE, expandedCollapsed))
  }

  fun onStartModuleAction(module: Module, lesson: Lesson) {
    logEvent(START_MODULE_ACTION, FeatureUsageData()
      .addData(MODULE_NAME, module.name)
      .addData(LESSON_ID, lesson.id)
      .addData(PROGRESS_PERCENTAGE, calcProgressPercentage(module))
      .addData(LANGUAGE, lesson.lang.toLowerCase()))
  }

  private fun calcProgressPercentage(module: Module): Int {
    val passedLessons: Int = module.lessons.filter { it.passed }.count()
    val totalLessons: Int = module.lessons.count()
    return passedLessons * 100 / totalLessons
  }

}