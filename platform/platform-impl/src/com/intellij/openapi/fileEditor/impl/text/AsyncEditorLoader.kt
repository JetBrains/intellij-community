// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.concurrency.captureThreadContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.EditorNotifications
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.AsyncProcessIcon
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.event.ChangeEvent
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class AsyncEditorLoader internal constructor(private val project: Project,
                                             private val provider: TextEditorProvider,
                                             @JvmField val coroutineScope: CoroutineScope,
                                             editor: EditorImpl,
                                             virtualFile: VirtualFile, task: Deferred<Unit>?) {
  private val tasks: List<Deferred<Unit>>
  init {
    val textEditorInit = coroutineScope.async(CoroutineName("HighlighterTextEditorInitializer")) {
      TextEditorImpl.setHighlighterToEditor(project, virtualFile, editor.document, editor)
    }
    tasks = if (task == null) listOf(textEditorInit) else listOf(textEditorInit, task)
  }
  /**
   * [delayedActions] contains either:
   * - empty list: the editor was not loaded
   * - list of runnables: the editor was not loaded and these runnables need to be run on load
   * - [LOADED]: the editor is loaded
   *  empty list was chosen to mark editor "not loaded" as early as possible, to avoid a narrow data race between TextEditorImpl instantiation and [AsyncEditorLoader.start] call
   */
  private val LOADED: List<Runnable> = listOf(Runnable {})
  private val delayedActions: AtomicReference<List<Runnable>> = AtomicReference(listOf())
  private val delayedScrollState = AtomicReference<DelayedScrollState?>()

  companion object {
    internal val OPENED_IN_BULK: Key<Boolean> = Key.create("EditorSplitters.opened.in.bulk")

    @Internal
    fun isOpenedInBulk(file: VirtualFile): Boolean = file.getUserData(OPENED_IN_BULK) != null

    private fun findTextEditor(editor: Editor): TextEditor? {
      val project = editor.project
      val virtualFile = editor.virtualFile
      if (project == null || virtualFile == null) return null
      return FileEditorManager.getInstance(project).getAllEditors(virtualFile).find { f -> f is TextEditor } as TextEditor?
    }

    @JvmStatic
    @RequiresEdt
    fun performWhenLoaded(editor: Editor, runnable: Runnable) {
      val asyncLoader = (findTextEditor(editor) as? TextEditorImpl)?.asyncLoader
      if (asyncLoader == null) {
        runnable.run()
      }
      else {
        asyncLoader.performWhenLoaded(runnable)
      }
    }

    internal suspend fun waitForLoaded(editor: Editor) {
      if (!isEditorLoaded(editor)) {
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          suspendCancellableCoroutine {
            performWhenLoaded(editor) { it.resume(Unit) }
          }
        }
      }
    }

    @JvmStatic
    fun isEditorLoaded(editor: Editor): Boolean {
      val textEditor = findTextEditor(editor)
      if (textEditor !is TextEditorImpl) return true
      return textEditor.isLoaded()
    }
  }

  @RequiresEdt
  private fun performWhenLoaded(runnable: Runnable) {
    val toRunLater = captureThreadContext(runnable)
    val newActions = delayedActions.updateAndGet { oldActions: List<Runnable> ->
      if (oldActions == LOADED || oldActions.contains(toRunLater)) oldActions
      else oldActions + toRunLater
    }
    if (!newActions.contains(toRunLater)) {
      runnable.run()
    }
  }

  // executed in the same EDT task where TextEditorImpl is created
  @Internal
  @RequiresEdt
  fun start(textEditor: TextEditorImpl) {
    val editor = textEditor.editor

    if (ApplicationManager.getApplication().isUnitTestMode) {
      startInTests(tasks = tasks)
      return
    }

    coroutineScope.launch(CoroutineName("AsyncEditorLoader.wait")) {
      // don't show another loading indicator on project open - use 3-second delay yet
      val indicatorJob = showLoadingIndicator(
        startDelay = if (isOpenedInBulk(textEditor.file)) 3_000.milliseconds else 300.milliseconds,
        addUi = textEditor.component::addLoadingDecoratorUi
      )
      // await instead of join to get errors here
      tasks.awaitAll()

      indicatorJob.cancel()

      withContext(Dispatchers.EDT + CoroutineName("execute delayed actions")) {
        editor.scrollingModel.disableAnimation()
        try {
          markLoadedAndExecuteDelayedActions()
        }
        finally {
          editor.scrollingModel.enableAnimation()
        }
      }
      EditorNotifications.getInstance(project).updateNotifications(textEditor.file)
    }
  }

  private fun markLoadedAndExecuteDelayedActions() {
    val delayedActions = delayedActions.getAndSet(LOADED)
    for (action in delayedActions) {
      action.run()
    }
  }

  private fun startInTests(tasks: List<Deferred<*>>) {
    runWithModalProgressBlocking(project, "") {
      tasks.awaitAll()
    }
    markLoadedAndExecuteDelayedActions()
  }

  @RequiresReadLock
  fun getEditorState(level: FileEditorStateLevel, editor: Editor): TextEditorState {
    return provider.getStateImpl(project, editor, level)
  }

  @RequiresEdt
  fun setEditorState(state: TextEditorState, exactState: Boolean, editor: EditorEx) {
    provider.setStateImpl(project = project, editor = editor, state = state, exactState = exactState)

    if (!isEditorLoaded(editor)) {
      delayedScrollState.set(DelayedScrollState(relativeCaretPosition = state.relativeCaretPosition, exactState = exactState))
      coroutineScope.launch(Dispatchers.EDT) {
        val delayedScrollState = delayedScrollState.getAndSet(null) ?: return@launch
        restoreCaretPosition(editor = editor, delayedScrollState = delayedScrollState, coroutineScope = coroutineScope)
      }
    }
  }

  internal fun dispose() {
    coroutineScope.cancel()
  }

  internal fun isLoaded(): Boolean {
    return delayedActions.get() == LOADED
  }
}

private class DelayedScrollState(@JvmField val relativeCaretPosition: Int, @JvmField val exactState: Boolean)

@RequiresEdt
private fun restoreCaretPosition(editor: EditorEx, delayedScrollState: DelayedScrollState, coroutineScope: CoroutineScope) {
  fun doScroll() {
    scrollToCaret(editor = editor,
                  exactState = delayedScrollState.exactState,
                  relativeCaretPosition = delayedScrollState.relativeCaretPosition)
  }

  val viewport = editor.scrollPane.viewport

  fun isReady(): Boolean {
    val extentSize = viewport.extentSize?.takeIf { it.width != 0 && it.height != 0 } ?: viewport.preferredSize
    return extentSize.width != 0 && extentSize.height != 0
  }

  if (viewport.isShowing && isReady()) {
    doScroll()
  }
  else {
    var listenerHandle: DisposableHandle? = null
    val listener = object : javax.swing.event.ChangeListener {
      override fun stateChanged(e: ChangeEvent) {
        if (!viewport.isShowing || !isReady()) {
          return
        }

        viewport.removeChangeListener(this)
        listenerHandle?.dispose()

        doScroll()
      }
    }
    listenerHandle = coroutineScope.coroutineContext.job.invokeOnCompletion {
      viewport.removeChangeListener(listener)
    }
    viewport.addChangeListener(listener)
  }
}

private fun CoroutineScope.showLoadingIndicator(startDelay: Duration, addUi: (component: JComponent) -> Unit): Job {
  require(startDelay >= Duration.ZERO)

  val scheduleTime = System.currentTimeMillis()
  return launch {
    delay((startDelay.inWholeMilliseconds - (System.currentTimeMillis() - scheduleTime)).coerceAtLeast(0))

    val processIcon = withContext(Dispatchers.EDT) {
      val processIcon = AsyncProcessIcon.createBig(/* coroutineScope = */ this@launch)
      addUi(processIcon)
      processIcon
    }

    awaitCancellationAndInvoke {
      withContext(Dispatchers.EDT) {
        processIcon.suspend()
        processIcon.parent.remove(processIcon)
      }
    }
  }
}
