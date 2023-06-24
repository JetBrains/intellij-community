// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.concurrency.captureThreadContext
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
      val continuation = runWithModalProgressBlocking(project, "") {
        configureHighlighter(highlighterDeferred, editor)
        textEditor.loadEditorInBackground()
      }
      editor.putUserData(ASYNC_LOADER, null)
      continuation?.run()
      while (true) {
        (delayedActions.pollFirst() ?: break).run()
      }
    }
    else {
      // don't show yet another loading indicator on project open - use 3-second delay
      val loadingDecorator = AsyncLoadingDecorator(
        startDelay = if (EditorsSplitters.isOpenedInBulk(textEditor.file)) 3_000.milliseconds else 300.milliseconds,
      )

      // `openEditorImpl` uses runWithModalProgressBlocking,
      // but an async editor load is performed in the background, out of the `openEditorImpl` call
      val modality = ModalityState.any().asContextElement()

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

      val editorComponent = textEditor.component
      val indicatorJob = loadingDecorator.startLoading(scope = coroutineScope + modality, addUi = editorComponent::addLoadingDecoratorUi)

      coroutineScope.launch(modality) {
        configureHighlighter(highlighterDeferred, editor)
      }

      val continuationDeferred = coroutineScope.async {
        textEditor.loadEditorInBackground()
      }

      coroutineScope.launch(modality) {
        val continuation = continuationDeferred.await()
        loadingDecorator.stopLoading(scope = this, indicatorJob = indicatorJob)
        withContext(Dispatchers.EDT) {
          loaded(continuation = continuation, editor = editor, editorComponent = editorComponent, coverComponent)
        }
        EditorNotifications.getInstance(project).updateNotifications(textEditor.file)
      }
    }
  }

  private fun loaded(continuation: Runnable?, editor: Editor, editorComponent: TextEditorComponent, coverComponent: JComponent) {
    editor.putUserData(ASYNC_LOADER, null)

    runCatching {
      continuation?.run()
    }.getOrLogException(logger<AsyncEditorLoader>())

    editor.scrollingModel.disableAnimation()
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
    provider.setStateImpl(project, editor, state, exactState)
  }

  internal fun dispose() {
    coroutineScope.cancel()
  }
}

private suspend fun configureHighlighter(highlighterDeferred: Deferred<EditorHighlighter>, editor: EditorEx) {
  val highlighter = highlighterDeferred.await()
  withContext(Dispatchers.EDT) {
    editor.settings.setLanguageSupplier { getDocumentLanguage(editor) }
    editor.highlighter = highlighter
  }
}