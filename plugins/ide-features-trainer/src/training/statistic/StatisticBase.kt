// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.TimeoutUtil
import training.lang.LangManager
import training.learn.CourseManager
import training.learn.interfaces.Lesson
import training.learn.interfaces.Module
import training.statistic.FeatureUsageStatisticConsts.COMPLETED_COUNT
import training.statistic.FeatureUsageStatisticConsts.COURSE_SIZE
import training.statistic.FeatureUsageStatisticConsts.DURATION
import training.statistic.FeatureUsageStatisticConsts.EXPAND_WELCOME_PANEL
import training.statistic.FeatureUsageStatisticConsts.LANGUAGE
import training.statistic.FeatureUsageStatisticConsts.LESSON_ID
import training.statistic.FeatureUsageStatisticConsts.MODULE_NAME
import training.statistic.FeatureUsageStatisticConsts.PASSED
import training.statistic.FeatureUsageStatisticConsts.PROGRESS
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
      .addData(LANGUAGE, courseLanguage()))
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
      .addData(LANGUAGE, courseLanguage())
      .addData(DURATION, delta))

    logEvent(PROGRESS, FeatureUsageData()
      .addData(LESSON_ID, lesson.id)
      .addData(COMPLETED_COUNT, completedCount())
      .addData(COURSE_SIZE, CourseManager.instance.lessonsForModules.size)
      .addData(LANGUAGE, courseLanguage()))
  }

  private fun logEvent(event: String, featureUsageData: FeatureUsageData) {
    FUCounterUsageLogger.getInstance().logEvent(GROUP_ID, event, featureUsageData)
  }

  fun onStartModuleAction(module: Module) {
    logEvent(START_MODULE_ACTION, FeatureUsageData()
      .addData(MODULE_NAME, module.name)
      .addData(LANGUAGE, courseLanguage()))
  }

  fun onExpandWelcomeScreenPanel() {
    logEvent(EXPAND_WELCOME_PANEL, FeatureUsageData()
      .addData(LANGUAGE, courseLanguage()))
  }

  private fun courseLanguage() = LangManager.getInstance().getLangSupport()?.primaryLanguage?.toLowerCase() ?: ""

  private fun completedCount(): Int = CourseManager.instance.lessonsForModules.count { it.passed }
}