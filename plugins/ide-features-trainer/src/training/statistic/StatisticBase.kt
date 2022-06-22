// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.statistic

import com.intellij.ide.TipsOfTheDayUsagesCollector.TipInfoValidationRule
import com.intellij.ide.plugins.PluginManager
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.impl.DefaultKeymapImpl
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.TimeoutUtil
import training.lang.LangManager
import training.learn.CourseManager
import training.learn.course.IftModule
import training.learn.course.Lesson
import training.learn.lesson.LessonManager
import training.statistic.FeatureUsageStatisticConsts.ACTION_ID
import training.statistic.FeatureUsageStatisticConsts.COMPLETED_COUNT
import training.statistic.FeatureUsageStatisticConsts.COURSE_SIZE
import training.statistic.FeatureUsageStatisticConsts.DURATION
import training.statistic.FeatureUsageStatisticConsts.EXPAND_WELCOME_PANEL
import training.statistic.FeatureUsageStatisticConsts.FEEDBACK_ENTRY_PLACE
import training.statistic.FeatureUsageStatisticConsts.FEEDBACK_EXPERIENCED_USER
import training.statistic.FeatureUsageStatisticConsts.FEEDBACK_HAS_BEEN_SENT
import training.statistic.FeatureUsageStatisticConsts.FEEDBACK_LIKENESS_ANSWER
import training.statistic.FeatureUsageStatisticConsts.FEEDBACK_OPENED_VIA_NOTIFICATION
import training.statistic.FeatureUsageStatisticConsts.HELP_LINK_CLICKED
import training.statistic.FeatureUsageStatisticConsts.INTERNAL_PROBLEM
import training.statistic.FeatureUsageStatisticConsts.KEYMAP_SCHEME
import training.statistic.FeatureUsageStatisticConsts.LANGUAGE
import training.statistic.FeatureUsageStatisticConsts.LAST_BUILD_LEARNING_OPENED
import training.statistic.FeatureUsageStatisticConsts.LEARN_PROJECT_OPENED_FIRST_TIME
import training.statistic.FeatureUsageStatisticConsts.LEARN_PROJECT_OPENING_WAY
import training.statistic.FeatureUsageStatisticConsts.LESSON_ID
import training.statistic.FeatureUsageStatisticConsts.LESSON_LINK_CLICKED_FROM_TIP
import training.statistic.FeatureUsageStatisticConsts.LESSON_STARTING_WAY
import training.statistic.FeatureUsageStatisticConsts.MODULE_NAME
import training.statistic.FeatureUsageStatisticConsts.NEED_SHOW_NEW_LESSONS_NOTIFICATIONS
import training.statistic.FeatureUsageStatisticConsts.NEW_LESSONS_COUNT
import training.statistic.FeatureUsageStatisticConsts.NEW_LESSONS_NOTIFICATION_SHOWN
import training.statistic.FeatureUsageStatisticConsts.NON_LEARNING_PROJECT_OPENED
import training.statistic.FeatureUsageStatisticConsts.ONBOARDING_FEEDBACK_DIALOG_RESULT
import training.statistic.FeatureUsageStatisticConsts.ONBOARDING_FEEDBACK_NOTIFICATION_SHOWN
import training.statistic.FeatureUsageStatisticConsts.PASSED
import training.statistic.FeatureUsageStatisticConsts.PROBLEM
import training.statistic.FeatureUsageStatisticConsts.PROGRESS
import training.statistic.FeatureUsageStatisticConsts.REASON
import training.statistic.FeatureUsageStatisticConsts.RESTORE
import training.statistic.FeatureUsageStatisticConsts.SHORTCUT_CLICKED
import training.statistic.FeatureUsageStatisticConsts.SHOULD_SHOW_NEW_LESSONS
import training.statistic.FeatureUsageStatisticConsts.SHOW_NEW_LESSONS
import training.statistic.FeatureUsageStatisticConsts.START
import training.statistic.FeatureUsageStatisticConsts.START_MODULE_ACTION
import training.statistic.FeatureUsageStatisticConsts.STOPPED
import training.statistic.FeatureUsageStatisticConsts.TASK_ID
import training.statistic.FeatureUsageStatisticConsts.TIP_FILENAME
import training.util.KeymapUtil
import java.awt.event.KeyEvent
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JOptionPane

enum class LessonStartingWay {
  NEXT_BUTTON, PREV_BUTTON, RESTART_BUTTON, RESTORE_LINK, ONBOARDING_PROMOTER, LEARN_TAB, TIP_AND_TRICK_PROMOTER, NO_SDK_RESTART
}

internal enum class FeedbackEntryPlace {
  WELCOME_SCREEN, LEARNING_PROJECT, ANOTHER_PROJECT
}

internal enum class FeedbackLikenessAnswer {
  NO_ANSWER, LIKE, DISLIKE
}

enum class LearningInternalProblems {
  NO_SDK_CONFIGURED, // Before learning start we are trying to autoconfigure SDK or at least ask about location
}

internal class StatisticBase : CounterUsagesCollector() {
  override fun getGroup() = GROUP

  private data class LessonProgress(val lessonId: String, val taskId: Int)

  enum class LearnProjectOpeningWay {
    LEARN_IDE, ONBOARDING_PROMOTER
  }

  enum class LessonStopReason {
    CLOSE_PROJECT, RESTART, CLOSE_FILE, OPEN_MODULES, OPEN_NEXT_OR_PREV_LESSON, EXIT_LINK
  }

  companion object {
    private val LOG = logger<StatisticBase>()
    private val sessionLessonTimestamp: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
    private var prevRestoreLessonProgress: LessonProgress = LessonProgress("", 0)
    private val GROUP: EventLogGroup = EventLogGroup("ideFeaturesTrainer", 18)

    var isLearnProjectCloseLogged = false

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
    private val learnProjectOpeningWayField = EventFields.Enum<LearnProjectOpeningWay>(LEARN_PROJECT_OPENING_WAY)
    private val reasonField = EventFields.Enum<LessonStopReason>(REASON)
    private val newLessonsCount = EventFields.Int(NEW_LESSONS_COUNT)
    private val showNewLessonsState = EventFields.Boolean(SHOULD_SHOW_NEW_LESSONS)
    private val tipFilenameField = EventFields.StringValidatedByCustomRule(TIP_FILENAME, TipInfoValidationRule.RULE_ID)
    private val lessonStartingWayField = EventFields.Enum<LessonStartingWay>(LESSON_STARTING_WAY)
    private val feedbackEntryPlace = EventFields.Enum<FeedbackEntryPlace>(FEEDBACK_ENTRY_PLACE)
    private val feedbackHasBeenSent = EventFields.Boolean(FEEDBACK_HAS_BEEN_SENT)
    private val feedbackOpenedViaNotification = EventFields.Boolean(FEEDBACK_OPENED_VIA_NOTIFICATION)
    private val feedbackLikenessAnswer = EventFields.Enum<FeedbackLikenessAnswer>(FEEDBACK_LIKENESS_ANSWER)
    private val feedbackExperiencedUser = EventFields.Boolean(FEEDBACK_EXPERIENCED_USER)
    private val internalProblemField = EventFields.Enum<LearningInternalProblems>(PROBLEM)

    private val lastBuildLearningOpened = object : PrimitiveEventField<String?>() {
      override val name: String = LAST_BUILD_LEARNING_OPENED
      override val validationRule: List<String>
        get() = listOf("{regexp#version}")

      override fun addData(fuData: FeatureUsageData, value: String?) {
        if (value != null) {
          fuData.addData(name, value)
        }
      }
    }

    // EVENTS
    private val lessonStartedEvent: EventId3<String?, String?, LessonStartingWay> = GROUP.registerEvent(START, lessonIdField, languageField,
                                                                                                        lessonStartingWayField)
    private val lessonPassedEvent: EventId3<String?, String?, Long> = GROUP.registerEvent(PASSED, lessonIdField, languageField,
                                                                                          EventFields.Long(DURATION))
    private val lessonStoppedEvent = GROUP.registerVarargEvent(STOPPED, lessonIdField, taskIdField, languageField, reasonField)
    private val progressUpdatedEvent = GROUP.registerVarargEvent(PROGRESS, lessonIdField, completedCountField, courseSizeField,
                                                                 languageField)
    private val moduleStartedEvent: EventId2<String?, String?> = GROUP.registerEvent(START_MODULE_ACTION, moduleNameField, languageField)
    private val welcomeScreenPanelExpandedEvent: EventId1<String?> = GROUP.registerEvent(EXPAND_WELCOME_PANEL, languageField)
    private val shortcutClickedEvent = GROUP.registerVarargEvent(SHORTCUT_CLICKED, inputEventField, keymapSchemeField,
                                                                 lessonIdField, taskIdField, actionIdField, versionField)
    private val restorePerformedEvent = GROUP.registerVarargEvent(RESTORE, lessonIdField, taskIdField, versionField)
    private val learnProjectOpenedFirstTimeEvent: EventId2<LearnProjectOpeningWay, String?> =
      GROUP.registerEvent(LEARN_PROJECT_OPENED_FIRST_TIME, learnProjectOpeningWayField, languageField)
    private val nonLearningProjectOpened: EventId1<LearnProjectOpeningWay> =
      GROUP.registerEvent(NON_LEARNING_PROJECT_OPENED, learnProjectOpeningWayField)

    private val newLessonsNotificationShown =
      GROUP.registerEvent(NEW_LESSONS_NOTIFICATION_SHOWN, newLessonsCount, lastBuildLearningOpened)
    private val showNewLessonsEvent =
      GROUP.registerEvent(SHOW_NEW_LESSONS, newLessonsCount, lastBuildLearningOpened)
    private val needShowNewLessonsNotifications =
      GROUP.registerEvent(NEED_SHOW_NEW_LESSONS_NOTIFICATIONS, newLessonsCount, lastBuildLearningOpened, showNewLessonsState)

    private val internalProblem =
      GROUP.registerEvent(INTERNAL_PROBLEM, internalProblemField, lessonIdField, languageField)

    private val lessonLinkClickedFromTip = GROUP.registerEvent(LESSON_LINK_CLICKED_FROM_TIP, lessonIdField, languageField, tipFilenameField)
    private val helpLinkClicked = GROUP.registerEvent(HELP_LINK_CLICKED, lessonIdField, languageField)

    private val onboardingFeedbackNotificationShown = GROUP.registerEvent(ONBOARDING_FEEDBACK_NOTIFICATION_SHOWN,
                                                                          feedbackEntryPlace)

    private val onboardingFeedbackDialogResult = GROUP.registerVarargEvent(ONBOARDING_FEEDBACK_DIALOG_RESULT,
                                                                           feedbackEntryPlace,
                                                                           feedbackHasBeenSent,
                                                                           feedbackOpenedViaNotification,
                                                                           feedbackLikenessAnswer,
                                                                           feedbackExperiencedUser,
                                                                           )

    // LOGGING
    fun logLessonStarted(lesson: Lesson, startingWay: LessonStartingWay) {
      sessionLessonTimestamp[lesson.id] = System.nanoTime()
      lessonStartedEvent.log(lesson.id, courseLanguage(), startingWay)
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

    fun logLessonStopped(reason: LessonStopReason) {
      val lessonManager = LessonManager.instance
      if (lessonManager.lessonIsRunning()) {
        val lessonId = lessonManager.currentLesson!!.id
        val taskId = lessonManager.currentLessonExecutor!!.currentTaskIndex
        lessonStoppedEvent.log(lessonIdField with lessonId,
                               taskIdField with taskId.toString(),
                               languageField with courseLanguage(),
                               reasonField with reason
        )
        if (reason == LessonStopReason.CLOSE_PROJECT || reason == LessonStopReason.EXIT_LINK) {
          isLearnProjectCloseLogged = true
        }
      }
    }

    fun logModuleStarted(module: IftModule) {
      moduleStartedEvent.log(module.id, courseLanguage())
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
      val curLessonProgress = LessonProgress(lesson.id, taskId)
      if (curLessonProgress != prevRestoreLessonProgress) {
        prevRestoreLessonProgress = curLessonProgress
        restorePerformedEvent.log(lessonIdField with lesson.id,
                                  taskIdField with taskId.toString(),
                                  versionField with getPluginVersion(lesson))
      }
    }

    fun logLearnProjectOpenedForTheFirstTime(way: LearnProjectOpeningWay) {
      val langManager = LangManager.getInstance()
      val langSupport = langManager.getLangSupport() ?: return
      if (langManager.getLearningProjectPath(langSupport) == null) {
        LearnProjectState.instance.firstTimeOpenedWay = way
        learnProjectOpenedFirstTimeEvent.log(way, courseLanguage())
      }
    }

    fun logNonLearningProjectOpened(way: LearnProjectOpeningWay) {
      nonLearningProjectOpened.log(way)
    }

    fun logNewLessonsNotification(newLessonsCount: Int, previousOpenedVersion: BuildNumber?) {
      newLessonsNotificationShown.log(newLessonsCount, previousOpenedVersion?.asString())
    }

    fun logShowNewLessonsEvent(newLessonsCount: Int, previousOpenedVersion: BuildNumber?) {
      showNewLessonsEvent.log(newLessonsCount, previousOpenedVersion?.asString())
    }

    fun logShowNewLessonsNotificationState(newLessonsCount: Int, previousOpenedVersion: BuildNumber?, showNewLessons: Boolean) {
      needShowNewLessonsNotifications.log(newLessonsCount, previousOpenedVersion?.asString(), showNewLessons)
    }

    fun logLessonLinkClickedFromTip(lessonId: String, tipFilename: String) {
      lessonLinkClickedFromTip.log(lessonId, courseLanguage(), tipFilename)
    }

    fun logHelpLinkClicked(lessonId: String) {
      helpLinkClicked.log(lessonId, courseLanguage())
    }

    fun logOnboardingFeedbackNotification(place: FeedbackEntryPlace) {
      onboardingFeedbackNotificationShown.log(place)
    }

    fun logOnboardingFeedbackDialogResult(place: FeedbackEntryPlace,
                                          hasBeenSent: Boolean,
                                          openedViaNotification: Boolean,
                                          likenessAnswer: FeedbackLikenessAnswer,
                                          experiencedUser: Boolean) {
      onboardingFeedbackDialogResult.log(
        feedbackEntryPlace with place,
        feedbackHasBeenSent with hasBeenSent,
        feedbackOpenedViaNotification with openedViaNotification,
        feedbackLikenessAnswer with likenessAnswer,
        feedbackExperiencedUser with experiencedUser
      )
    }

    fun logLearningProblem(problem: LearningInternalProblems, lesson: Lesson) {
      internalProblem.log(problem, lesson.id, courseLanguage())
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
      return PluginManager.getPluginByClass(lesson::class.java)?.version
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