// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import training.dsl.TaskContext
import training.learn.exceptons.NoTextEditor
import training.learn.lesson.LessonManager
import training.statistic.StatisticBase
import training.ui.LearningUiManager
import training.util.DataLoader
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import java.util.concurrent.CompletableFuture

private val LOG = logger<ActionsRecorder>()

class ActionsRecorder(private val project: Project,
                      private val document: Document?,
                      parentDisposable: Disposable) : Disposable {

  private val documentListeners: MutableList<DocumentListener> = mutableListOf()
  // TODO: do we really need a lot of listeners?
  private val actionListeners: MutableList<AnActionListener> = mutableListOf()
  private val eventDispatchers: MutableList<IdeEventQueue.EventDispatcher> = mutableListOf()

  var disposed = false
    private set

  private val busConnection = ApplicationManager.getApplication().messageBus.connect(this)

  /** Currently registered command listener */
  private var commandListener: CommandListener? = null

  // TODO: I suspect that editor listener could be replaced by command listener. Need to check it
  private var editorListener: FileEditorManagerListener? = null

  private var checkCallback: (() -> Unit)? = null

  private var focusChangeListener: PropertyChangeListener? = null

  init {
    Disposer.register(parentDisposable, this)

    // We could not unregister a listener (it will be done in dispose)
    // So the simple solution is to use a proxy

    busConnection.subscribe(AnActionListener.TOPIC, object : AnActionListener {
      override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        actionListeners.forEach { it.beforeActionPerformed(action, dataContext, event) }
      }

      override fun afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        actionListeners.forEach { it.afterActionPerformed(action, dataContext, event) }
      }

      override fun beforeEditorTyping(c: Char, dataContext: DataContext) {
        actionListeners.forEach { it.beforeEditorTyping(c, dataContext) }
      }
    })

    // This listener allows to track a lot of IDE state changes
    busConnection.subscribe(CommandListener.TOPIC, object : CommandListener {
      override fun commandStarted(event: CommandEvent) {
        commandListener?.commandStarted(event)
      }

      override fun beforeCommandFinished(event: CommandEvent) {
        commandListener?.beforeCommandFinished(event)
      }

      override fun commandFinished(event: CommandEvent) {
        commandListener?.commandFinished(event)
      }

      override fun undoTransparentActionStarted() {
        commandListener?.undoTransparentActionStarted()
      }

      override fun beforeUndoTransparentActionFinished() {
        commandListener?.beforeUndoTransparentActionFinished()
      }

      override fun undoTransparentActionFinished() {
        commandListener?.undoTransparentActionFinished()
      }
    })
    busConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        editorListener?.fileClosed(source, file)
      }

      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        editorListener?.fileOpened(source, file)
      }

      override fun fileOpenedSync(source: FileEditorManager,
                                  file: VirtualFile,
                                  editors: Pair<Array<FileEditor>, Array<FileEditorProvider>>) {
        editorListener?.fileOpenedSync(source, file, editors)
      }

      override fun selectionChanged(event: FileEditorManagerEvent) {
        editorListener?.selectionChanged(event)
      }
    })
  }

  override fun dispose() {
    removeListeners()
    disposed = true
    Disposer.dispose(this)
  }

  fun futureActionOnStart(actionId: String, check: () -> Boolean): CompletableFuture<Boolean> {
    val future: CompletableFuture<Boolean> = CompletableFuture()
    val actionListener = object : AnActionListener {
      override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        checkAndCancelForException(future) { getActionId(action) == actionId && check() }
      }
    }
    actionListeners.add(actionListener)
    return future
  }

  fun futureActionAndCheckAround(actionId: String, before: () -> Unit, check: () -> Boolean): CompletableFuture<Boolean> {
    val future: CompletableFuture<Boolean> = CompletableFuture()
    val actionListener = object : AnActionListener {
      override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        val caughtActionId: String? = ActionManager.getInstance().getId(action)
        if (actionId == caughtActionId) {
          before()
        }
        else if (caughtActionId != null) {
          // remove additional state listener to check caret positions and so on
          commandListener = null
        }
      }

      override fun afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        if (actionId == ActionManager.getInstance().getId(action)) {
          val complete = checkComplete()
          if (!complete) {
            addSimpleCommandListener { checkComplete() }
          }
        }
      }

      override fun beforeEditorTyping(c: Char, dataContext: DataContext) {}

      private fun checkComplete(): Boolean = checkAndCancelForException(future, check)
    }
    actionListeners.add(actionListener)
    return future
  }

  fun futureAction(actionId: String): CompletableFuture<Boolean> {
    val future: CompletableFuture<Boolean> = CompletableFuture()
    registerActionListener { caughtActionId, _ -> if (actionId == caughtActionId) future.complete(true) }
    return future
  }

  fun futureAction(checkId: (String) -> Boolean): CompletableFuture<Boolean> {
    val future: CompletableFuture<Boolean> = CompletableFuture()
    registerActionListener { caughtActionId, _ -> if (checkId(caughtActionId)) future.complete(true) }
    return future
  }

  fun futureListActions(listOfActions: List<String>): CompletableFuture<Boolean> {
    val future: CompletableFuture<Boolean> = CompletableFuture()
    val mutableListOfActions = listOfActions.toMutableList()
    registerActionListener { caughtActionId, _ ->
      if (mutableListOfActions.isNotEmpty() && mutableListOfActions.first() == caughtActionId) mutableListOfActions.removeAt(0)
      if (mutableListOfActions.isEmpty()) future.complete(true)
    }
    editorListener = object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        event.newFile?.name.let {
          if (mutableListOfActions.isNotEmpty() && mutableListOfActions.first() == event.newFile?.name) mutableListOfActions.removeAt(0)
          if (mutableListOfActions.isEmpty()) future.complete(true)
        }
      }
    }
    return future
  }

  fun futureCheck(checkFunction: () -> Boolean): CompletableFuture<Boolean> {
    val future: CompletableFuture<Boolean> = CompletableFuture()

    val check: () -> Unit = { checkAndCancelForException(future, checkFunction) }
    checkCallback = check

    addKeyEventListener { check() }
    document?.addDocumentListener(createDocumentListener { check() })
    addSimpleCommandListener(check)
    actionListeners.add(object : AnActionListener {
      override fun afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        check()
      }
    })

    PropertyChangeListener { check() }.let {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", it)
      focusChangeListener = it
    }

    return future
  }

  fun tryToCheckCallback() {
    checkCallback?.let { it() }
  }

  private fun addSimpleCommandListener(check: () -> Unit) {
    commandListener = object : CommandListener {
      override fun commandFinished(event: CommandEvent) {
        check()
      }
    }
  }

  private fun addKeyEventListener(onKeyEvent: () -> Unit) {
    val myEventDispatcher: IdeEventQueue.EventDispatcher = IdeEventQueue.EventDispatcher { e ->
      if (e is KeyEvent ||
          (e as? MouseEvent)?.id == MouseEvent.MOUSE_RELEASED ||
          (e as? MouseEvent)?.id == MouseEvent.MOUSE_CLICKED) onKeyEvent()
      false
    }
    eventDispatchers.add(myEventDispatcher)
    IdeEventQueue.getInstance().addDispatcher(myEventDispatcher, this)
  }

  private fun createDocumentListener(onDocumentChange: () -> Unit): DocumentListener {
    val documentListener = object : DocumentListener {

      override fun beforeDocumentChange(event: DocumentEvent) {}

      override fun documentChanged(event: DocumentEvent) {
        if (document != null && PsiDocumentManager.getInstance(project).isUncommited(document)) {
          ApplicationManager.getApplication().invokeLater {
            if (!disposed && !project.isDisposed) {
              PsiDocumentManager.getInstance(project).commitAndRunReadAction { onDocumentChange() }
            }
          }
        }
      }
    }
    documentListeners.add(documentListener)
    return documentListener
  }

  private fun registerActionListener(processAction: (actionId: String, project: Project) -> Unit): AnActionListener {
    val actionListener = object : AnActionListener {
      override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        processAction(getActionId(action), project)
      }
    }
    actionListeners.add(actionListener)
    return actionListener
  }


  private fun removeListeners() {
    if (documentListeners.isNotEmpty() && document != null) documentListeners.forEach { document.removeDocumentListener(it) }
    if (eventDispatchers.isNotEmpty()) eventDispatchers.forEach { IdeEventQueue.getInstance().removeDispatcher(it) }
    actionListeners.clear()
    documentListeners.clear()
    eventDispatchers.clear()
    commandListener = null
    editorListener = null
    focusChangeListener?.let { KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", it) }
  }

  private fun getActionId(action: AnAction): String {
    val actionId = ActionManager.getInstance().getId(action) ?: action.javaClass.name
    if (DataLoader.liveMode) {
      println(actionId)
    }
    return actionId
  }

  private fun checkAndCancelForException(future: CompletableFuture<Boolean>, check: () -> Boolean): Boolean {
    try {
      if (!future.isDone && !future.isCancelled && check()) {
        future.complete(true)
        return true
      }
      return false
    }
    catch (e: NoTextEditor) {
      val activeToolWindow = LearningUiManager.activeToolWindow
      val lesson = LessonManager.instance.currentLesson
      if (activeToolWindow != null && lesson != null) {
        val notification = TaskContext.RestoreNotification(LearnBundle.message("learn.restore.notification.editor.closed")) {
          CourseManager.instance.openLesson(activeToolWindow.project, lesson)
        }
        LessonManager.instance.setRestoreNotification(notification)
      }
      if (!StatisticBase.isLearnProjectClosing) {
        StatisticBase.logLessonStopped(StatisticBase.LessonStopReason.CLOSE_FILE)
      }
      LessonManager.instance.stopLesson()
    }
    catch (e: Exception) {
      LOG.error("IFT check produces exception", e)
    }
    return false
  }
}
