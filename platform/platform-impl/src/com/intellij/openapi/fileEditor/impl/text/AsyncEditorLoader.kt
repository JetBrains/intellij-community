// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.concurrency.captureThreadContext
import com.intellij.diagnostic.subtask
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl.Companion.getDocumentLanguage
import com.intellij.openapi.progress.runWithModalProgressBlocking
import com.intellij.openapi.progress.withRawProgressReporter
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.AsyncLoadingDecorator
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import javax.swing.event.ChangeEvent
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.milliseconds

private val ASYNC_LOADER = Key.create<AsyncEditorLoader>("ASYNC_LOADER")

class AsyncEditorLoader internal constructor(private val project: Project,
                                             private val provider: TextEditorProvider,
                                             @JvmField val coroutineScope: CoroutineScope) {
  private val delayedActions = ArrayDeque<Runnable>()
  private var delayedScrollState: DelayedScrollState? = null

  companion object {
    @JvmStatic
    @RequiresEdt
    fun performWhenLoaded(editor: Editor, runnable: Runnable) {
      val loader = editor.getUserData(ASYNC_LOADER)
      loader?.delayedActions?.add(captureThreadContext(runnable)) ?: runnable.run()
    }

    internal suspend fun waitForLoaded(editor: Editor) {
      if (editor.getUserData(ASYNC_LOADER) != null) {
        withContext(Dispatchers.EDT) {
          suspendCoroutine {
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

  fun createHighlighterAsync(document: Document, file: VirtualFile): Deferred<EditorHighlighter> {
    return coroutineScope.async {
      val scheme = EditorColorsManager.getInstance().globalScheme
      val editorHighlighterFactory = EditorHighlighterFactory.getInstance()
      readAction {
        val highlighter = editorHighlighterFactory.createEditorHighlighter(file, scheme, project)
        highlighter.setText(document.immutableCharSequence)
        highlighter
      }
    }
  }

  // executed in the same EDT task where TextEditorImpl is created
  @Internal
  @RequiresEdt
  fun start(textEditor: TextEditorImpl, tasks: List<suspend (EditorEx) -> Unit>) {
    val editor = textEditor.editor
    editor.putUserData(ASYNC_LOADER, this)

    if (ApplicationManager.getApplication().isUnitTestMode) {
      startInTests(tasks = tasks, editor = editor, textEditor = textEditor)
      return
    }

    val continuationDeferred = coroutineScope.async {
      textEditor.loadEditorInBackground()
    }

    // `openEditorImpl` uses runWithModalProgressBlocking,
    // but an async editor load is performed in the background, out of the `openEditorImpl` call
    val modality = ModalityState.any().asContextElement()

    val taskJob = coroutineScope.async(modality) {
      for (task in tasks) {
        async { task(editor) }
      }
    }

    // don't show yet another loading indicator on project open - use 3-second delay
    val loadingDecorator = AsyncLoadingDecorator(
      startDelay = if (EditorsSplitters.isOpenedInBulk(textEditor.file)) 3_000.milliseconds else 300.milliseconds,
    )

    val editorComponent = textEditor.component
    val indicatorJob = loadingDecorator.startLoading(scope = coroutineScope + modality, addUi = editorComponent::addLoadingDecoratorUi)

    coroutineScope.launch(modality) {
      val continuation = continuationDeferred.await()
      // await instead of joint to get errors here
      taskJob.await()

      indicatorJob.cancel()
      withContext(Dispatchers.EDT) {
        editor.putUserData(ASYNC_LOADER, null)
        editor.scrollingModel.disableAnimation()
        try {
          restoreCaretState(editor)

          loadingDecorator.stopLoading(scope = this)

          runCatching {
            continuation?.run()
          }.getOrLogException(logger<AsyncEditorLoader>())

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

  fun restoreCaretState(editor: EditorEx) {
    delayedScrollState?.let {
      delayedScrollState = null
      restoreCaretPosition(editor = editor, delayedScrollState = it, coroutineScope = coroutineScope)
    }
  }

  private fun startInTests(tasks: List<suspend (EditorEx) -> Unit>, editor: EditorEx, textEditor: TextEditorImpl) {
    val continuation = runWithModalProgressBlocking(project, "") {
      // required for switch to
      withRawProgressReporter {
        tasks.map { it(editor) }
        textEditor.loadEditorInBackground()
      }
    }
    editor.putUserData(ASYNC_LOADER, null)

    continuation?.run()

    while (true) {
      (delayedActions.pollFirst() ?: break).run()
    }
  }

  @RequiresReadLock
  fun getEditorState(level: FileEditorStateLevel, editor: Editor): TextEditorState {
    return provider.getStateImpl(project, editor, level)
  }

  @RequiresEdt
  fun setEditorState(state: TextEditorState, exactState: Boolean, editor: Editor) {
    if (!isEditorLoaded(editor)) {
      delayedScrollState = DelayedScrollState(relativeCaretPosition = state.relativeCaretPosition, exactState = exactState)
    }

    provider.setStateImpl(project, editor, state, exactState)
  }

  internal fun dispose() {
    coroutineScope.cancel()
  }
}

private class DelayedScrollState(@JvmField val relativeCaretPosition: Int, @JvmField val exactState: Boolean)

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

suspend fun configureHighlighter(highlighterDeferred: Deferred<EditorHighlighter>, editor: EditorEx) {
  val highlighter = highlighterDeferred.await()
  subtask("editor highlighter set", Dispatchers.EDT) {
    editor.settings.setLanguageSupplier { getDocumentLanguage(editor) }
    editor.highlighter = highlighter
  }
}