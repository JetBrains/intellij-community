// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.concurrency.captureThreadContext
import com.intellij.diagnostic.subtask
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.AsyncLoadingDecorator
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.GridBagConstraints
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.util.*
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.milliseconds

private val ASYNC_LOADER = Key.create<AsyncEditorLoader>("ASYNC_LOADER")

class AsyncEditorLoader internal constructor(private val project: Project,
                                             private val provider: TextEditorProvider,
                                             private val coroutineScope: CoroutineScope) {
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
  fun start(textEditor: TextEditorImpl, highlighterDeferred: Deferred<EditorHighlighter>) {
    val editor = textEditor.editor
    editor.putUserData(ASYNC_LOADER, this)

    if (ApplicationManager.getApplication().isUnitTestMode) {
      startInTests(highlighterDeferred = highlighterDeferred, editor = editor, textEditor = textEditor)
      return
    }

    // don't show yet another loading indicator on project open - use 3-second delay
    val loadingDecorator = AsyncLoadingDecorator(
      startDelay = if (EditorsSplitters.isOpenedInBulk(textEditor.file)) 3_000.milliseconds else 300.milliseconds,
    )

    // `openEditorImpl` uses runWithModalProgressBlocking,
    // but an async editor load is performed in the background, out of the `openEditorImpl` call
    val modality = ModalityState.any().asContextElement()

    val coverComponent = addCoverComponent(editor = editor, textEditor = textEditor)

    val editorComponent = textEditor.component
    val indicatorJob = loadingDecorator.startLoading(scope = coroutineScope + modality, addUi = editorComponent::addLoadingDecoratorUi)

    val configureHighlighterJob = coroutineScope.launch(modality) {
      configureHighlighter(highlighterDeferred, editor)
    }

    val continuationDeferred = coroutineScope.async {
      textEditor.loadEditorInBackground()
    }

    coroutineScope.launch(modality) {
      val continuation = continuationDeferred.await()
      configureHighlighterJob.join()
      loadingDecorator.stopLoading(scope = this, indicatorJob = indicatorJob)
      withContext(Dispatchers.EDT) {
        loaded(continuation = continuation, editor = editor, editorComponent = editorComponent, coverComponent)
      }
      EditorNotifications.getInstance(project).updateNotifications(textEditor.file)
    }
  }

  private fun startInTests(highlighterDeferred: Deferred<EditorHighlighter>, editor: EditorEx, textEditor: TextEditorImpl) {
    val continuation = runWithModalProgressBlocking(project, "") {
      configureHighlighter(highlighterDeferred, editor)
      textEditor.loadEditorInBackground()
    }
    editor.putUserData(ASYNC_LOADER, null)
    continuation?.run()
    delayedScrollState?.let {
      delayedScrollState = null
      scrollToCaret(editor = editor,
                    exactState = it.exactState,
                    relativeCaretPosition = it.relativeCaretPosition)
    }
    while (true) {
      (delayedActions.pollFirst() ?: break).run()
    }
  }

  private fun loaded(continuation: Runnable?, editor: EditorEx, editorComponent: TextEditorComponent, coverComponent: JComponent) {
    editor.putUserData(ASYNC_LOADER, null)

    runCatching {
      continuation?.run()
    }.getOrLogException(logger<AsyncEditorLoader>())

    editor.scrollingModel.disableAnimation()

    delayedScrollState?.let {
      delayedScrollState = null
      restoreCaretPosition(editor = editor, delayedScrollState = it, coroutineScope = coroutineScope)
    }

    try {
      while (true) {
        (delayedActions.pollFirst() ?: break).run()
      }
    }
    finally {
      editorComponent.remove(coverComponent)

      editor.scrollingModel.enableAnimation()
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
  fun scrollWhenReady() {
    fun isReady(): Boolean {
      val extentSize = editor.scrollPane.viewport.extentSize
      return extentSize.width != 0 && extentSize.height != 0
    }

    fun doScroll() {
      scrollToCaret(editor = editor,
                    exactState = delayedScrollState.exactState,
                    relativeCaretPosition = delayedScrollState.relativeCaretPosition)
    }

    if (isReady()) {
      doScroll()
    }
    else {
      coroutineScope.launch(ModalityState.any().asContextElement()) {
        var done = false
        var attemptCount = 0
        while (!done) {
          attemptCount++
          done = withContext(Dispatchers.EDT) {
            if (isReady()) {
              doScroll()
              true
            }
            else if (attemptCount > 3) {
              thisLogger().warn("Cannot wait for a ready scroll pane, scroll now")
              doScroll()
              true
            }
            else {
              false
            }
          }
        }
      }
    }
  }

  val component = editor.contentComponent
  if (component.isShowing) {
    scrollWhenReady()
  }
  else {
    var listenerHandle: DisposableHandle? = null
    val listener = object : HierarchyListener {
      override fun hierarchyChanged(event: HierarchyEvent) {
        if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() <= 0) {
          return
        }

        component.removeHierarchyListener(this)
        listenerHandle?.dispose()

        scrollWhenReady()
      }
    }
    listenerHandle = coroutineScope.coroutineContext.job.invokeOnCompletion {
      component.removeHierarchyListener(listener)
    }
    component.addHierarchyListener(listener)
  }
}

private suspend fun configureHighlighter(highlighterDeferred: Deferred<EditorHighlighter>, editor: EditorEx) {
  val highlighter = highlighterDeferred.await()
  subtask("editor highlighter set", Dispatchers.EDT) {
    editor.settings.setLanguageSupplier { getDocumentLanguage(editor) }
    editor.highlighter = highlighter
  }
}

private fun addCoverComponent(editor: EditorEx, textEditor: TextEditorImpl): JPanel {
  val coverComponent = JPanel()
  coverComponent.background = editor.backgroundColor
  JLayeredPane.putLayer(coverComponent, JLayeredPane.DRAG_LAYER - 1)
  textEditor.component.__add(coverComponent, GridBagConstraints().also {
    it.gridx = 0
    it.gridy = 0
    it.weightx = 1.0
    it.weighty = 1.0
    it.fill = GridBagConstraints.BOTH
  })
  return coverComponent
}