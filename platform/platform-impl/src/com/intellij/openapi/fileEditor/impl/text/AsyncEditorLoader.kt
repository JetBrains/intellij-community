// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AsyncEditorLoader internal constructor(private val project: Project,
                                             private val provider: TextEditorProvider,
                                             private val coroutineScope: CoroutineScope) {
  private val delayedActions = ArrayDeque<Runnable>()

  companion object {
    private val ASYNC_LOADER = Key.create<AsyncEditorLoader>("ASYNC_LOADER")
    private val LOG = logger<AsyncEditorLoader>()

    @JvmStatic
    @RequiresEdt
    fun performWhenLoaded(editor: Editor, runnable: Runnable) {
      val loader = editor.getUserData(ASYNC_LOADER)
      loader?.delayedActions?.add(runnable) ?: runnable.run()
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

  @Internal
  @RequiresEdt
  fun start(textEditor: TextEditorImpl, highlighterDeferred: Deferred<EditorHighlighter>) {
    val editor = textEditor.editor
    editor.putUserData(ASYNC_LOADER, this)

    if (ApplicationManager.getApplication().isUnitTestMode) {
      val continuation = runBlockingModal(project, "") {
        textEditor.loadEditorInBackground(highlighterDeferred)
      }
      editor.putUserData(ASYNC_LOADER, null)
      continuation.run()
      while (true) {
        (delayedActions.pollFirst() ?: break).run()
      }
    }
    else {
      val continuationDeferred = coroutineScope.async {
        textEditor.loadEditorInBackground(highlighterDeferred)
      }

      // do not show half-ready editor (not highlighted)
      editor.component.isVisible = false

      val editorComponent = textEditor.component
      val modality = ModalityState.current().asContextElement()
      val indicatorJob = editorComponent.loadingDecorator.startLoading(scope = coroutineScope + modality,
                                                                       addUi = editorComponent::addLoadingDecoratorUi)

      coroutineScope.launch(modality) {
        val continuation = continuationDeferred.await()
        editorComponent.loadingDecorator.stopLoading(scope = this, indicatorJob = indicatorJob)
        withContext(Dispatchers.EDT) {
          loaded(continuation = continuation, editor = editor)
        }
        EditorNotifications.getInstance(project).updateNotifications(textEditor.file)
      }
    }
  }

  @RequiresEdt
  private fun loaded(continuation: Runnable, editor: Editor) {
    editor.putUserData(ASYNC_LOADER, null)

    runCatching {
      continuation.run()
    }.getOrLogException(LOG)

    // should be before executing delayed actions - editor state restoration maybe a delayed action, and it uses `doWhenFirstShown`,
    // for performance reasons better to avoid
    editor.component.isVisible = true

    editor.scrollingModel.disableAnimation()
    while (true) {
      (delayedActions.pollFirst() ?: break).run()
    }
    editor.scrollingModel.enableAnimation()
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