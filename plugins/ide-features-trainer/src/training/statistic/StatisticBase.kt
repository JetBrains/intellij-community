// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.DefaultKeymapImpl
import com.intellij.util.TimeoutUtil
import training.keymap.KeymapUtil
import training.lang.LangManager
import training.learn.CourseManager
import training.learn.interfaces.Lesson
import training.learn.interfaces.Module
import training.learn.lesson.LessonManager
import training.statistic.FeatureUsageStatisticConsts.ACTION_ID
import training.statistic.FeatureUsageStatisticConsts.COMPLETED_COUNT
import training.statistic.FeatureUsageStatisticConsts.COURSE_SIZE
import training.statistic.FeatureUsageStatisticConsts.DURATION
import training.statistic.FeatureUsageStatisticConsts.EXPAND_WELCOME_PANEL
import training.statistic.FeatureUsageStatisticConsts.KEYMAP_SCHEME
import training.statistic.FeatureUsageStatisticConsts.LANGUAGE
import training.statistic.FeatureUsageStatisticConsts.LESSON_ID
import training.statistic.FeatureUsageStatisticConsts.MODULE_NAME
import training.statistic.FeatureUsageStatisticConsts.PASSED
import training.statistic.FeatureUsageStatisticConsts.PROGRESS
import training.statistic.FeatureUsageStatisticConsts.RESTORE
import training.statistic.FeatureUsageStatisticConsts.SHORTCUT_CLICKED
import training.statistic.FeatureUsageStatisticConsts.START
import training.statistic.FeatureUsageStatisticConsts.START_MODULE_ACTION
import training.statistic.FeatureUsageStatisticConsts.TASK_ID
import java.awt.event.KeyEvent
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JOptionPane

internal class StatisticBase : CounterUsagesCollector() {
  override fun getGroup() = GROUP

  companion object {
    private val LOG = logger<StatisticBase>()
    private val sessionLessonTimestamp: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
    private val GROUP: EventLogGroup = EventLogGroup("ideFeaturesTrainer", 7)

    // FIELDS
    private val lessonIdField = EventFields.StringValidatedByCustomRule(LESSON_ID, LESSON_ID)
    private val languageField = EventFields.StringValidatedByCustomRule(LANGUAGE, LANGUAGE)
    private val completedCountField = EventFields.Int(COMPLETED_COUNT)
    private val courseSizeField = EventFields.Int(COURSE_SIZE)
    private val moduleNameField = EventFields.StringValidatedByCustomRule(MODULE_NAME, MODULE_NAME)
    private val taskIdField = EventFields.StringValidatedByCustomRule(TASK_ID, TASK_ID)
    private val actionIdField = EventFields.StringValidatedByCustomRule(ACTION_ID, ACTION_ID)
    private val keymapSchemeField = EventFields.StringValidatedByCustomRule(KEYMAP_SCHEME, KEYMAP_SCHEME)
    private val versionField = EventFields.Version
    private val inputEventField = EventFields.InputEvent

    // EVENTS
    private val lessonStartedEvent: EventId2<String?, String?> = GROUP.registerEvent(START, lessonIdField, languageField)
    private val lessonPassedEvent: EventId3<String?, String?, Long> = GROUP.registerEvent(PASSED, lessonIdField, languageField,
                                                                                          EventFields.Long(DURATION))
    private val progressUpdatedEvent = GROUP.registerVarargEvent(PROGRESS, lessonIdField, completedCountField, courseSizeField,
                                                                 languageField)
    private val moduleStartedEvent: EventId2<String?, String?> = GROUP.registerEvent(START_MODULE_ACTION, moduleNameField, languageField)
    private val welcomeScreenPanelExpandedEvent: EventId1<String?> = GROUP.registerEvent(EXPAND_WELCOME_PANEL, languageField)
    private val shortcutClickedEvent = GROUP.registerVarargEvent(SHORTCUT_CLICKED, inputEventField, keymapSchemeField,
                                                                 lessonIdField, taskIdField, actionIdField, versionField)
    private val restorePerformedEvent = GROUP.registerVarargEvent(RESTORE, lessonIdField, taskIdField, versionField)

    // LOGGING
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

    fun logShortcutClicked(actionId: String) {
      val lessonManager = LessonManager.instance
      if (lessonManager.lessonIsRunning()) {
        val lesson = lessonManager.currentLesson!!
        val keymap = getDefaultKeymap() ?: return
        shortcutClickedEvent.log(inputEventField with createInputEvent(actionId),
                                 keymapSchemeField with keymap.name,
                                 lessonIdField with lesson.id,
                                 taskIdField with lessonManager.currentLessonExecutor?.currentTaskIndex.toString(),
                                 actionIdField with actionId,
                                 versionField with getPluginVersion(lesson))
      }
    }

    fun logRestorePerformed(lesson: Lesson, taskId: Int) {
      restorePerformedEvent.log(lessonIdField with lesson.id,
                                taskIdField with taskId.toString(),
                                versionField with getPluginVersion(lesson))
    }

    private fun courseLanguage() = LangManager.getInstance().getLangSupport()?.primaryLanguage?.toLowerCase() ?: ""

    private fun completedCount(): Int = CourseManager.instance.lessonsForModules.count { it.passed }

    private fun createInputEvent(actionId: String): FusInputEvent? {
      val keyStroke = KeymapUtil.getShortcutByActionId(actionId) ?: return null
      val inputEvent = KeyEvent(JOptionPane.getRootFrame(),
                                KeyEvent.KEY_PRESSED,
                                System.currentTimeMillis(),
                                keyStroke.modifiers,
                                keyStroke.keyCode,
                                keyStroke.keyChar,
                                KeyEvent.KEY_LOCATION_STANDARD)
      return FusInputEvent(inputEvent, "")
    }

    private fun getPluginVersion(lesson: Lesson): String? {
      val pluginId = PluginManagerCore.getPluginByClassName(lesson::class.java.name)
      return PluginManagerCore.getPlugin(pluginId)?.version
    }

    private fun getDefaultKeymap(): Keymap? {
      val keymap = KeymapManager.getInstance().activeKeymap
      if (keymap is DefaultKeymapImpl) {
        return keymap
      }
      return keymap.parent as? DefaultKeymapImpl
    }
  }
}