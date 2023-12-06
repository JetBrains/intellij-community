// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.concurrency.captureThreadContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.util.progress.withRawProgressReporter
import com.intellij.ui.EditorNotifications
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.AsyncProcessIcon
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.event.ChangeEvent
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private val ASYNC_LOADER = Key.create<AsyncEditorLoader>("ASYNC_LOADER")

class AsyncEditorLoader internal constructor(private val project: Project,
                                             private val provider: TextEditorProvider,
                                             @JvmField val coroutineScope: CoroutineScope) {
  private val delayedActions = ArrayDeque<Runnable>()
  private val delayedScrollState = AtomicReference<DelayedScrollState?>()

  companion object {
    internal val OPENED_IN_BULK = Key.create<Boolean>("EditorSplitters.opened.in.bulk")

    @Internal
    fun isOpenedInBulk(file: VirtualFile): Boolean = file.getUserData(OPENED_IN_BULK) != null

    @JvmStatic
    @RequiresEdt
    fun performWhenLoaded(editor: Editor, runnable: Runnable) {
      val loader = editor.getUserData(ASYNC_LOADER)
      loader?.delayedActions?.add(captureThreadContext(runnable)) ?: runnable.run()
    }

    internal suspend fun waitForLoaded(editor: Editor) {
      if (editor.getUserData(ASYNC_LOADER) != null) {
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          suspendCancellableCoroutine {
            performWhenLoaded(editor) { it.resume(Unit) }
          }
        }
      }
    }

    @JvmStatic
    fun isEditorLoaded(editor: Editor): Boolean {
      return editor.getUserData(ASYNC_LOADER) == null
    }
  }

  // executed in the same EDT task where TextEditorImpl is created
  @Internal
  @RequiresEdt
  fun start(textEditor: TextEditorImpl, tasks: List<Deferred<*>>) {
    val editor = textEditor.editor
    editor.putUserData(ASYNC_LOADER, this)

    if (ApplicationManager.getApplication().isUnitTestMode) {
      startInTests(tasks = tasks, editor = editor)
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
        editor.putUserData(ASYNC_LOADER, null)
        editor.scrollingModel.disableAnimation()
        try {
          while (true) {
            (delayedActions.pollFirst() ?: break).run()
          }
        }
        finally {
          editor.scrollingModel.enableAnimation()
        }
      }
      EditorNotifications.getInstance(project).updateNotifications(textEditor.file)
    }
  }

  private fun startInTests(tasks: List<Deferred<*>>, editor: EditorEx) {
    runWithModalProgressBlocking(project, "") {
      // required for switch to
      withRawProgressReporter {
        tasks.awaitAll()
      }
    }
    editor.putUserData(ASYNC_LOADER, null)

    while (true) {
      (delayedActions.pollFirst() ?: break).run()
    }
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
