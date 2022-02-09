// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.project.Project
import org.intellij.lang.annotations.Language
import training.dsl.TaskContext
import training.dsl.TaskTextProperties
import training.dsl.impl.LessonExecutor
import training.dsl.impl.OpenPassedContext
import training.learn.course.KLesson
import training.learn.course.Lesson
import training.ui.*
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

  internal var currentLessonExecutor: LessonExecutor? = null
    private set

  var shownRestoreNotification: TaskContext.RestoreNotification? = null
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
    learnPanel.learnToolWindow.showGotItAboutRestart()
  }

  internal fun initDslLesson(editor: Editor?, cLesson: Lesson, lessonExecutor: LessonExecutor) {
    initLesson(editor, cLesson)
    currentLessonExecutor = lessonExecutor
  }

  fun lessonIsRunning(): Boolean = currentLessonExecutor?.hasBeenStopped?.not() ?: false

  fun stopLesson() = stopLesson(false)

  private fun stopLesson(lessonPassed: Boolean) {
    shownRestoreNotification = null
    currentLessonExecutor?.takeIf { !it.hasBeenStopped }?.let {
      it.stopLesson()
      currentLessonExecutor = null
    }
    if (!lessonPassed) {  // highlights already cleared in case of passed lesson
      LearningUiHighlightingManager.clearHighlights()
    }
  }

  private fun initLesson(editor: Editor?, cLesson: Lesson) {
    val learnPanel = learnPanel ?: return
    stopLesson()
    currentLesson = cLesson
    learnPanel.reinitMe(cLesson)
    if (cLesson.existedFile == null) {
      clearEditor(editor)
    }
    learnPanel.scrollToTheStart()
  }

  fun addMessage(@Language("HTML") text: String,
                 isInformer: Boolean = false,
                 visualNumber: Int? = null,
                 useInternalParagraphStyle: Boolean = false,
                 textProperties: TaskTextProperties? = null) {
    val state = if (isInformer) LessonMessagePane.MessageState.INFORMER else LessonMessagePane.MessageState.NORMAL
    learnPanel?.addMessage(text, LessonMessagePane.MessageProperties(state, visualNumber, useInternalParagraphStyle, textProperties))
  }

  fun addInactiveMessage(message: String, visualNumber: Int?) {
    learnPanel?.addMessage(message, LessonMessagePane.MessageProperties(LessonMessagePane.MessageState.INACTIVE, visualNumber))
  }

  fun removeInactiveMessages(number: Int) {
    learnPanel?.removeInactiveMessages(number)
  }

  fun resetMessagesNumber(number: Int) {
    shownRestoreNotification = null
    learnPanel?.resetMessagesNumber(number)
  }

  fun removeMessage(index: Int) {
    learnPanel?.removeMessage(index)
  }

  fun removeMessageAndRepaint(index: Int) {
    learnPanel?.let {
      it.removeMessage(index)
      it.lessonMessagePane.redrawMessages()
      it.adjustMessagesArea()
    }
  }

  fun messagesNumber(): Int = learnPanel?.messagesNumber() ?: 0

  fun passExercise() {
    learnPanel?.setPreviousMessagesPassed()
  }

  fun passLesson(cLesson: Lesson) {
    cLesson.pass()
    LearningUiHighlightingManager.clearHighlights()
    val learnPanel = learnPanel ?: return
    learnPanel.makeNextButtonSelected()
    stopLesson(true)
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
    val callback = Runnable {
      notification.callback()
      currentLessonExecutor?.taskInvokeLater {
        clearRestoreMessage()
      }
    }
    val message = MessagePart(" ${notification.message} ", MessagePart.MessageType.TEXT_BOLD)
    val restoreLink = MessagePart(notification.restoreLinkText, MessagePart.MessageType.LINK).also { it.runnable = callback }
    setNotification(listOf(message, restoreLink))
    shownRestoreNotification = notification
  }

  fun setWarningNotification(notification: TaskContext.RestoreNotification) {
    val messages = MessageFactory.convert(notification.message)
    setNotification(messages)
    shownRestoreNotification = notification
  }

  private fun setNotification(messages: List<MessagePart>) {
    clearRestoreMessage()
    val warningIconIndex = LearningUiManager.getIconIndex(AllIcons.General.NotificationWarning)
    val warningIconMessage = MessagePart(warningIconIndex, MessagePart.MessageType.ICON_IDX)
    val allMessages = mutableListOf(warningIconMessage).also { it.addAll(messages) }
    learnPanel?.addMessages(allMessages, LessonMessagePane.MessageProperties(LessonMessagePane.MessageState.RESTORE))
  }

  fun lessonShouldBeOpenedCompleted(lesson: Lesson): Boolean = lesson.passed && currentLesson != lesson

  fun focusTask() {
    if (lessonIsRunning()) {
      learnPanel?.focusCurrentMessage()
    }
  }

  companion object {
    @Volatile
    var externalTestActionsExecutor: Executor? = null

    val instance: LessonManager
      get() = service()

    private val LOG = logger<LessonManager>()
  }
}
