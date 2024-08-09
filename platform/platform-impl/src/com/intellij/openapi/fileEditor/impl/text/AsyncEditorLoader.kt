// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.openapi.fileEditor.impl.text

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.concurrency.ContextAwareRunnable
import com.intellij.concurrency.captureThreadContext
import com.intellij.concurrency.resetThreadContext
import com.intellij.openapi.application.*
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.EditorNotifications
import com.intellij.util.ArrayUtil
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.AnimatedIcon
import com.intellij.util.ui.AsyncProcessIcon
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.event.ChangeEvent
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val LOG: Logger = logger<AsyncEditorLoader>()

@Internal
class AsyncEditorLoader internal constructor(
  private val project: Project,
  private val provider: TextEditorProvider,
  @JvmField val coroutineScope: CoroutineScope,
) {
  /**
   * [delayedActions] contains either:
   * - empty array: the editor was not loaded
   * - list of runnables: the editor was not loaded and these runnables need to be run on load
   * - `null`: the editor is loaded
   *  empty list was chosen to mark editor "not loaded" as early as possible, to avoid a narrow data race between TextEditorImpl instantiation and [AsyncEditorLoader.start] call
   */
  private val delayedActions: AtomicReference<Array<Runnable>> = AtomicReference(ArrayUtil.EMPTY_RUNNABLE_ARRAY)
  private val delayedScrollState = AtomicReference<DelayedScrollState?>()

  companion object {
    @JvmField
    val ASYNC_LOADER: Key<AsyncEditorLoader> = Key.create("AsyncEditorLoader.isLoaded")

    /**
     * Invoke callback when the editor is successfully loaded. The callback will not be called if the loading was canceled.
     */
    @RequiresEdt
    fun performWhenLoaded(editor: Editor, runnable: Runnable) {
      val asyncLoader = editor.getUserData(ASYNC_LOADER)
      if (asyncLoader == null || asyncLoader.isLoaded()) {
        runnable.run()
      }
      else {
        asyncLoader.performWhenLoaded(runnable)
      }
    }

    internal suspend fun waitForCompleted(editor: Editor) {
      val asyncLoader = editor.getUserData(ASYNC_LOADER)?.takeIf { !it.isLoaded() } ?: return
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        suspendCancellableCoroutine { continuation ->
          // resume on editor close
          val handle = asyncLoader.coroutineScope.coroutineContext.job.invokeOnCompletion {
            continuation.resume(Unit)
          }
          asyncLoader.performWhenLoaded(ContextAwareRunnable {
            try {
              continuation.resume(Unit)
            }
            finally {
              handle.dispose()
            }
          })
        }
      }
    }

    @JvmStatic
    fun isEditorLoaded(editor: Editor): Boolean {
      val asyncLoader = editor.getUserData(ASYNC_LOADER)
      return asyncLoader == null || asyncLoader.isLoaded()
    }
  }

  @RequiresEdt
  private fun performWhenLoaded(runnable: Runnable) {
    val toRunLater = captureThreadContext(runnable)
    val newActions = delayedActions.updateAndGet { oldActions ->
      if (oldActions === null || oldActions.contains(toRunLater)) oldActions else oldActions + toRunLater
    }
    if (newActions === null || !newActions.contains(toRunLater)) {
      runnable.run()
    }
  }

  // executed in the same EDT task where TextEditorImpl is created
  @Internal
  @RequiresEdt
  fun start(textEditor: TextEditorImpl, task: Deferred<*>) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      startInTests(task = task)
      return
    }

    coroutineScope.launch(CoroutineName("AsyncEditorLoader.wait")) {
      val editorFileName = textEditor.file.name
      val indicatorJob = showLoadingIndicator(
        startDelay = 300.milliseconds,
        addUi = textEditor.component::addLoadingDecoratorUi,
        editorFileName = editorFileName,
      )
      // await instead of join to get errors here
      try {
        task.await()
        LOG.trace { "async editor task finished for $editorFileName" }
      }
      finally {
        indicatorJob.cancel()
      }

      withContext(Dispatchers.EDT + CoroutineName("execute delayed actions")) {
        // mark as loaded before daemonCodeAnalyzer restart
        // do it from EDT to avoid execution of any following scroll requests before already scheduled delayedActions
        textEditor.editor.putUserData(ASYNC_LOADER, null)

        val scrollingModel = textEditor.editor.scrollingModel
        scrollingModel.disableAnimation()
        try {
          writeIntentReadAction {
            executeDelayedActions(delayedActions.getAndSet(null))
          }
        }
        finally {
          scrollingModel.enableAnimation()
        }
      }
      project.serviceAsync<EditorNotifications>().scheduleUpdateNotifications(textEditor)
    }
      .invokeOnCompletion {
        // make sure that async loaded marked as completed
        delayedActions.set(null)

        // make sure the highlighting is restarted when the editor is finally loaded, because otherwise some crazy things happen,
        // for instance `FileEditor.getBackgroundHighlighter()` returning null, essentially stopping highlighting silently
        DaemonCodeAnalyzer.getInstance(project).restart()
      }
  }

  private fun executeDelayedActions(delayedActions: Array<Runnable>) {
    resetThreadContext().use {
      for (action in delayedActions) {
        action.run()
      }
    }
  }

  private fun startInTests(task: Deferred<*>) {
    runWithModalProgressBlocking(project, "") {
      task.await()
    }
    executeDelayedActions(delayedActions.getAndSet(null))
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

  fun isLoaded(): Boolean = delayedActions.get() == null
}

private class DelayedScrollState(@JvmField val relativeCaretPosition: Int, @JvmField val exactState: Boolean)

@RequiresEdt
private fun restoreCaretPosition(editor: EditorEx, delayedScrollState: DelayedScrollState, coroutineScope: CoroutineScope) {
  fun doScroll() {
    scrollToCaret(
      editor = editor,
      exactState = delayedScrollState.exactState,
      relativeCaretPosition = delayedScrollState.relativeCaretPosition,
    )
  }

  val viewport = editor.scrollPane.viewport

  fun isReady(): Boolean {
    val extentSize = viewport.extentSize
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

private fun CoroutineScope.showLoadingIndicator(
  startDelay: Duration,
  addUi: (component: JComponent) -> Unit,
  editorFileName: String,
): Job {
  require(startDelay >= Duration.ZERO)

  val scheduleTimeMs = System.currentTimeMillis()

  return launch {
    val delayBeforeIcon = max(0, startDelay.inWholeMilliseconds - (System.currentTimeMillis() - scheduleTimeMs))
    delay(delayBeforeIcon)

    val processIconRef = AtomicReference<AnimatedIcon>()

    awaitCancellationAndInvoke {
      withContext(Dispatchers.EDT) {
        val processIcon = processIconRef.getAndSet(null)
        if (processIcon != null) {
          processIcon.suspend()
          processIcon.parent.remove(processIcon)
          LOG.trace { "spinner icon removed for $editorFileName" }
        }
      }
    }

    withContext(Dispatchers.EDT) {
      val processIcon = AsyncProcessIcon.createBig(/* coroutineScope = */ this@launch)
      processIconRef.set(processIcon)
      addUi(processIcon)
      LOG.trace { "spinner icon created for $editorFileName" }
    }
  }
}
