// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
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

  @Internal
  @RequiresEdt
  fun start(textEditor: TextEditorImpl) {
    val editor = textEditor.editor
    editor.putUserData(ASYNC_LOADER, this)

    if (ApplicationManager.getApplication().isUnitTestMode) {
      val continuation = runBlockingModal(project, "") {
        loadEditor(textEditor)
      }
      editor.putUserData(ASYNC_LOADER, null)
      continuation.run()
      while (true) {
        (delayedActions.pollFirst() ?: break).run()
      }
    }
    else {
      val continuationDeferred = coroutineScope.async {
        loadEditor(textEditor)
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
        loaded(continuation = continuation, editor = editor)
        EditorNotifications.getInstance(project).updateNotifications(textEditor.file)
      }
    }
  }

  private suspend fun loaded(continuation: Runnable, editor: Editor) {
    withContext(Dispatchers.EDT) {
      editor.putUserData(ASYNC_LOADER, null)

      runCatching {
        continuation.run()
      }.getOrLogException(LOG)

      ensureActive()

      // should be before executing delayed actions - editor state restoration maybe a delayed action, and it uses `doWhenFirstShown`,
      // for performance reasons better to avoid
      editor.component.isVisible = true

      editor.scrollingModel.disableAnimation()
      while (true) {
        (delayedActions.pollFirst() ?: break).run()
        ensureActive()
      }
      editor.scrollingModel.enableAnimation()
    }
  }

  private suspend fun loadEditor(textEditor: TextEditorImpl): Runnable {
    return constrainedReadAction(ReadConstraint.withDocumentsCommitted(project)) { textEditor.loadEditorInBackground() }
  }

  @RequiresReadLock
  fun getEditorState(level: FileEditorStateLevel, editor: Editor): TextEditorState {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    return provider.getStateImpl(project, editor, level)
  }

  @RequiresEdt
  fun setEditorState(state: TextEditorState, exactState: Boolean, editor: Editor) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    provider.setStateImpl(project, editor, state, exactState)
  }

  internal fun dispose() {
    coroutineScope.cancel()
  }
}