// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
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

internal object StatisticBase : CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("ideFeaturesTrainer", 6)

  // FIELDS
  private val lessonIdField = EventFields.StringValidatedByCustomRule(LESSON_ID, LESSON_ID)
  private val languageField = EventFields.StringValidatedByCustomRule(LANGUAGE, LANGUAGE)
  private val completedCountField = EventFields.Int(COMPLETED_COUNT)
  private val courseSizeField = EventFields.Int(COURSE_SIZE)
  private val moduleNameField = EventFields.StringValidatedByCustomRule(MODULE_NAME, MODULE_NAME)

  // EVENTS
  private val lessonStartedEvent: EventId2<String?, String?> = GROUP.registerEvent(START, lessonIdField, languageField)
  private val lessonPassedEvent: EventId3<String?, String?, Long> = GROUP.registerEvent(PASSED, lessonIdField, languageField,
                                                                                        EventFields.Long(DURATION))
  private val progressUpdatedEvent = GROUP.registerVarargEvent(PROGRESS, lessonIdField, completedCountField, courseSizeField, languageField)
  private val moduleStartedEvent: EventId2<String?, String?> = GROUP.registerEvent(START_MODULE_ACTION, moduleNameField, languageField)
  private val welcomeScreenPanelExpandedEvent: EventId1<String?> = GROUP.registerEvent(EXPAND_WELCOME_PANEL, languageField)

  private val sessionLessonTimestamp: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
  private val LOG = logger<StatisticBase>()

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  fun logLessonStarted(lesson: Lesson) {
    sessionLessonTimestamp[lesson.id] = System.nanoTime()
    lessonStartedEvent.log(lesson.id, courseLanguage())
  }

  fun logLessonPassed(lesson: Lesson) {
    val timestamp = sessionLessonTimestamp[lesson.id]
    if (timestamp == null) {
      LOG.warn("Unable to find timestamp for a lesson: ${lesson.name}")
      return
    }
    val delta = TimeoutUtil.getDurationMillis(timestamp)
    lessonPassedEvent.log(lesson.id, courseLanguage(), delta)
    progressUpdatedEvent.log(lessonIdField with lesson.id,
                             completedCountField with completedCount(),
                             courseSizeField with CourseManager.instance.lessonsForModules.size,
                             languageField with courseLanguage())
  }

  fun logModuleStarted(module: Module) {
    moduleStartedEvent.log(module.name, courseLanguage())
  }

  fun logWelcomeScreenPanelExpanded() {
    welcomeScreenPanelExpandedEvent.log(courseLanguage())
  }

  private fun courseLanguage() = LangManager.getInstance().getLangSupport()?.primaryLanguage?.toLowerCase() ?: ""

  private fun completedCount(): Int = CourseManager.instance.lessonsForModules.count { it.passed }
}