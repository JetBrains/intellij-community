// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.util

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.DynamicBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.JBUI
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import training.lang.LangManager
import training.lang.LangSupport
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.course.Lesson
import training.learn.lesson.LessonManager
import training.learn.lesson.LessonStateManager
import training.ui.LEARN_TOOL_WINDOW_ID
import training.ui.LearnToolWindow
import training.ui.LearningUiManager
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Desktop
import java.awt.Dimension
import java.net.URI
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.swing.*

internal fun createNamedSingleThreadExecutor(name: String): ExecutorService {
  return Executors.newSingleThreadExecutor(ThreadFactoryBuilder().setNameFormat(name).build())
}

private val excludedLanguages: Map<String, Array<String>> = mapOf( //IDE name to language id
  "AppCode" to arrayOf("JavaScript"),
  "DataSpell" to arrayOf("Python"),
)

internal fun courseCanBeUsed(languageId: String): Boolean {
  val excludedCourses = excludedLanguages[ApplicationNamesInfo.getInstance().productName]
  return excludedCourses == null || !excludedCourses.contains(languageId)
}

internal fun findLanguageByID(id: String): com.intellij.lang.Language? {
  val effectiveId = if (id.equals("cpp", ignoreCase = true)) {
    "ObjectiveC"
  }
  else {
    id
  }
  return com.intellij.lang.Language.findLanguageByID(effectiveId)
}

internal fun createBalloon(@Nls text: String): Balloon = createBalloon(text, 3000)

internal fun createBalloon(@Nls text: String, delay: Long): Balloon {
  return JBPopupFactory.getInstance()
    .createHtmlTextBalloonBuilder(text, MessageType.WARNING, null)
    .setHideOnClickOutside(true)
    .setCloseButtonEnabled(true)
    .setHideOnKeyOutside(true)
    .setAnimationCycle(0)
    .setFadeoutTime(delay)
    .createBalloon()
}

internal const val trainerPluginConfigName: String = "ide-features-trainer.xml"

internal val featureTrainerVersion: String by lazy {
  PluginManager.getPluginByClass(CourseManager::class.java)?.version ?: "UNKNOWN"
}

val adaptToNotNativeLocalization: Boolean
  get() = Registry.`is`("ift.adapt.to.not.native.localization") || DynamicBundle.getLocale() != Locale.ENGLISH

internal fun clearTrainingProgress() {
  LessonManager.instance.stopLesson()
  LessonStateManager.resetPassedStatus()
  for (toolWindow in getAllLearnToolWindows()) {
    toolWindow.reinitViews()
    toolWindow.setModulesPanel()
  }
  LearningUiManager.activeToolWindow = null
}

internal fun resetPrimaryLanguage(newLanguageId: String): Boolean {
  val oldLanguageId = LangManager.getInstance().getLanguageId()
  if (newLanguageId != oldLanguageId) {
    LessonManager.instance.stopLesson()
    LangManager.getInstance().updateLangSupport(newLanguageId)
    LearningUiManager.activeToolWindow?.setModulesPanel()
    return true
  }
  return false
}

fun findLanguageSupport(project: Project): LangSupport? {
  val languageId = LangManager.getInstance().getLanguageId() ?: return null
  if (isLearningProject(project, languageId)) {
    return LangManager.getInstance().getLangSupport()
  }
  return null
}

fun isLearningProject(project: Project, languageId: String): Boolean {
  return FileUtil.pathsEqual(project.basePath, LangManager.getInstance().getLearningProjectPath(languageId))
}

fun getFeedbackLink(langSupport: LangSupport, ownRegistry: Boolean): String? {
  return getFeedbackLink(langSupport.primaryLanguage.lowercase(), ownRegistry)
}

fun getFeedbackLink(langName: String, ownRegistry: Boolean): String? {
  val needToShow = Registry.`is`("ift.show.feedback.link" + if (ownRegistry) ".$langName" else "", false)
  return if (needToShow) "https://surveys.jetbrains.com/s3/features-trainer-feedback-$langName" else null
}

val useShortOnboardingLesson: Boolean get() = Registry.`is`("ift.short.onboarding.lesson", false)

val switchOnExperimentalLessons: Boolean
  get() = Registry.`is`("ift.experimental.lessons", false)

val enableLessonsAndPromoters: Boolean
  get() = ExperimentalUI.isNewUI() || Registry.`is`("ift.enable.in.old.ui", false) || PlatformUtils.isDataSpell()

fun invokeActionForFocusContext(action: AnAction) {
  DataManager.getInstance().dataContextFromFocusAsync.onSuccess { dataContext ->
    invokeLater {
      val event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.LEARN_TOOLWINDOW, dataContext)
      event.presentation.isPerformGroup = true
      ActionUtil.performAction(action, event)
    }
  }
}

fun getCallBackActionId(@Language("devkit-action-id") actionId: String): String {
  val action = getActionById(actionId)
  return LearningUiManager.addCallback { invokeActionForFocusContext(action) }
}

fun openLinkInBrowser(link: String) {
  val desktop = if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null
  if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
    desktop.browse(URI(link))
  }
}

fun LinkLabel<Any>.wrapWithUrlPanel(): JPanel {
  val jPanel = JPanel()
  jPanel.isOpaque = false
  jPanel.layout = BoxLayout(jPanel, BoxLayout.LINE_AXIS)
  jPanel.add(this, BorderLayout.CENTER)
  jPanel.add(JLabel(AllIcons.Ide.External_link_arrow), BorderLayout.EAST)
  jPanel.maximumSize = jPanel.preferredSize
  jPanel.alignmentX = JPanel.LEFT_ALIGNMENT
  return jPanel
}

fun rigid(width: Int, height: Int): Component {
  return scaledRigid(JBUI.scale(width), JBUI.scale(height))
}

fun scaledRigid(width: Int, height: Int): Component {
  return (Box.createRigidArea(Dimension(width, height)) as JComponent).apply {
    alignmentX = Component.LEFT_ALIGNMENT
    alignmentY = Component.TOP_ALIGNMENT
  }
}

internal fun getLearnToolWindowForProject(project: Project): LearnToolWindow? {
  val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(LEARN_TOOL_WINDOW_ID) ?: return null
  val jComponent = toolWindow.contentManagerIfCreated?.contents?.singleOrNull()?.component
  return jComponent as? LearnToolWindow
}

internal fun getAllLearnToolWindows(): List<LearnToolWindow> {
  return getOpenedProjects().mapNotNull { getLearnToolWindowForProject(it) }.toList()
}

internal fun lessonOpenedInProject(project: Project?): Lesson? {
  return if (project != null && getLearnToolWindowForProject(project) != null) LessonManager.instance.currentLesson else null
}

fun getNextLessonForCurrent(): Lesson? {
  val lesson = LessonManager.instance.currentLesson ?: return null
  val lessonsForModules = CourseManager.instance.lessonsForModules
  val index = lessonsForModules.indexOf(lesson)
  if (index < 0 || index >= lessonsForModules.size - 1) return null
  return lessonsForModules[index + 1]
}

fun getPreviousLessonForCurrent(): Lesson? {
  val lesson = LessonManager.instance.currentLesson ?: return null
  val lessonsForModules = CourseManager.instance.lessonsForModules
  val index = lessonsForModules.indexOf(lesson)
  if (index <= 0) return null
  return lessonsForModules[index - 1]
}


@Nls
fun learningProgressString(lessons: List<Lesson>): String {
  val total = lessons.size
  var done = 0
  for (lesson in lessons) {
    if (lesson.passed) done++
  }
  return if (done == total)
    LearnBundle.message("learn.module.progress.completed")
  else
    LearnBundle.message("learn.module.progress", done, total)
}

fun learningToolWindow(project: Project): ToolWindow? {
  return ToolWindowManager.getInstance(project).getToolWindow(LEARN_TOOL_WINDOW_ID)
}

fun Any?.toNullableString(): String? {
  return if (this == null) null else excludeNullCheck(toString())
}

fun Any?.isToStringContains(string: String): Boolean {
  return this.toNullableString()?.contains(string) ?: false
}

fun getActionById(@Language("devkit-action-id") actionId: String): AnAction {
  return ActionManager.getInstance().getAction(actionId)
         ?: error("No action with id $actionId in ${ApplicationNamesInfo.getInstance().fullProductNameWithEdition}")
}

private fun excludeNullCheck(value: String?): String? {
  return value
}

internal fun String.replaceSpacesWithNonBreakSpace(): String = this.replace(" ", StringUtil.NON_BREAK_SPACE)

internal fun String.surroundWithNonBreakSpaces(): String {
  val spaces = "${StringUtil.NON_BREAK_SPACE}${StringUtil.NON_BREAK_SPACE}"
  return spaces + this + spaces
}

internal val iftPluginIsUsing: Boolean get() = LessonStateManager.getPassedLessonsNumber() >= 5

internal const val SHOW_NEW_LESSONS_NOTIFICATION = "ift.show.new.lessons.notification"
internal const val LEARNING_PANEL_OPENED_IN = "ift.learning.panel.opened.in"
internal val learningPanelWasOpenedInCurrentVersion: Boolean
  get() {
    val savedValue = PropertiesComponent.getInstance().getValue(LEARNING_PANEL_OPENED_IN) ?: return false
    val savedBuild = BuildNumber.fromString(savedValue) ?: return false
    return savedBuild >= ApplicationInfo.getInstance().build
  }

internal fun filterUnseenLessons(newLessons: List<Lesson>): List<Lesson> {
  val zeroBuild = BuildNumber("", 0, 0)
  val maxSeenVersion = newLessons.filter { it.passed }.maxOfOrNull { lesson ->
    lesson.properties.availableSince?.let { BuildNumber.fromString(it) }
    ?: zeroBuild
  }
  val unseenLessons = if (maxSeenVersion == null) newLessons
  else newLessons.filter { lesson ->
    (lesson.properties.availableSince?.let { BuildNumber.fromString(it) } ?: zeroBuild) > maxSeenVersion
  }
  return unseenLessons
}

val iftNotificationGroup: NotificationGroup
  get() = NotificationGroupManager.getInstance().getNotificationGroup("IDE Features Trainer")