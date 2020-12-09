// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.project.Project
import org.intellij.lang.annotations.Language
import training.commands.kotlin.TaskContext
import training.learn.interfaces.Lesson
import training.learn.lesson.kimpl.KLesson
import training.learn.lesson.kimpl.LessonExecutor
import training.learn.lesson.kimpl.OpenPassedContext
import training.ui.LearningUiHighlightingManager
import training.ui.LearningUiManager
import training.ui.LessonMessagePane
import training.ui.MessagePart
import training.ui.views.LearnPanel
import training.util.createNamedSingleThreadExecutor
import java.awt.Rectangle
import java.util.concurrent.Executor

@Service
class LessonManager {
  var currentLesson: Lesson? = null
    private set

  private val learnPanel: LearnPanel?
    get() = LearningUiManager.activeToolWindow?.learnPanel

  private var currentLessonExecutor: LessonExecutor? = null

  var shownRestoreNotification : TaskContext.RestoreNotification? = null
    private set

  val testActionsExecutor: Executor by lazy {
    externalTestActionsExecutor ?: createNamedSingleThreadExecutor("TestLearningPlugin")
  }

  init {
    val connect = ApplicationManager.getApplication().messageBus.connect()
    connect.subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
      override fun activeKeymapChanged(keymap: Keymap?) {
        learnPanel?.lessonMessagePane?.redrawMessages()
      }

      override fun shortcutChanged(keymap: Keymap, actionId: String) {
        learnPanel?.lessonMessagePane?.redrawMessages()
      }
    })
  }

  internal fun clearCurrentLesson() {
    currentLesson = null
  }

  internal fun openLessonPassed(lesson: KLesson, project: Project) {
    val learnPanel = learnPanel ?: error("No learn panel")
    initLesson(null, lesson)
    learnPanel.scrollToNewMessages = false
    OpenPassedContext(project).apply(lesson.lessonContent)
    learnPanel.scrollRectToVisible(Rectangle(0, 0, 1, 1))
    learnPanel.makeNextButtonSelected()
    learnPanel.learnToolWindow?.showGotItAboutRestart()
  }

  internal fun initDslLesson(editor: Editor?, cLesson: Lesson, lessonExecutor: LessonExecutor) {
    initLesson(editor, cLesson)
    currentLessonExecutor = lessonExecutor
  }

  internal fun lessonIsRunning() : Boolean = currentLessonExecutor?.hasBeenStopped?.not() ?: false

  internal fun stopLesson() {
    shownRestoreNotification = null
    currentLessonExecutor?.takeIf { !it.hasBeenStopped }?.let {
      it.lesson.onStop()
      it.stopLesson()
      currentLessonExecutor = null
    }
    LearningUiHighlightingManager.clearHighlights()
  }

  private fun initLesson(editor: Editor?, cLesson: Lesson) {
    val learnPanel = learnPanel ?: return
    stopLesson()
    currentLesson = cLesson
    learnPanel.reinitMe(cLesson)

    learnPanel.setLessonName(cLesson.name)
    val module = cLesson.module
    val moduleName = module.name
    learnPanel.setModuleName(moduleName)
    if (cLesson.existedFile == null) {
      clearEditor(editor)
    }
  }

  fun addMessage(@Language("HTML") text: String, isInformer: Boolean = false) {
    val state = if (isInformer) LessonMessagePane.MessageState.INFORMER else LessonMessagePane.MessageState.NORMAL
    learnPanel?.addMessage(text, state)
  }

  fun addInactiveMessages(messages: List<String>) {
    for (m in messages) learnPanel?.addMessage(m, state = LessonMessagePane.MessageState.INACTIVE)
  }

  fun removeInactiveMessages(number: Int) {
    learnPanel?.removeInactiveMessages(number)
  }

  fun resetMessagesNumber(number: Int) {
    shownRestoreNotification = null
    learnPanel?.resetMessagesNumber(number)
  }

  fun messagesNumber(): Int = learnPanel?.messagesNumber() ?: 0

  fun passExercise() {
    learnPanel?.setPreviousMessagesPassed()
  }

  fun passLesson(cLesson: Lesson) {
    cLesson.pass()
    LearningUiHighlightingManager.clearHighlights()
    val learnPanel = learnPanel ?: return
    learnPanel.setLessonPassed()
    learnPanel.makeNextButtonSelected()
    learnPanel.updateUI()
    stopLesson()
  }


  private fun clearEditor(editor: Editor?) {
    ApplicationManager.getApplication().runWriteAction {
      if (editor != null) {
        val document = editor.document
        try {
          document.setText("")
        }
        catch (e: Exception) {
          LOG.error(e)
          System.err.println("Unable to update text in editor!")
        }

      }
    }
  }

  fun clearRestoreMessage() {
    if (shownRestoreNotification != null) {
      learnPanel?.clearRestoreMessage()
      shownRestoreNotification = null
    }
  }

  fun setRestoreNotification(notification: TaskContext.RestoreNotification) {
    clearRestoreMessage()
    val proposalText = notification.message
    val warningIconIndex = LearningUiManager.getIconIndex(AllIcons.General.NotificationWarning)
    val callback = Runnable {
      notification.callback()
      invokeLater {
        clearRestoreMessage()
      }
    }
    learnPanel?.addMessages(listOf(MessagePart(warningIconIndex, MessagePart.MessageType.ICON_IDX),
                                   MessagePart(" ${proposalText} ", MessagePart.MessageType.TEXT_BOLD),
                                   MessagePart("Restore", MessagePart.MessageType.LINK).also { it.runnable = callback }
    ), LessonMessagePane.MessageState.RESTORE)
    shownRestoreNotification = notification
  }

  companion object {
    @Volatile
    var externalTestActionsExecutor: Executor? = null

    val instance: LessonManager
      get() = service()

    private val LOG = logger<LessonManager>()
  }
}
