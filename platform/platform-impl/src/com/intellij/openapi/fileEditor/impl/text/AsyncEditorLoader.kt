// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.progress.runBlockingModal
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.EditorNotifications
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Service(Service.Level.PROJECT)
internal class AsyncEditorLoaderService(private val coroutineScope: CoroutineScope) {
  fun start(textEditor: TextEditorImpl, editorComponent: TextEditorComponent, provider: TextEditorProvider): AsyncEditorLoader {
    val loader = AsyncEditorLoader(textEditor = textEditor,
                                   editorComponent = editorComponent,
                                   provider = provider,
                                   coroutineScope = coroutineScope.childScope(supervisor = false))

    loader.start()
    return loader
  }
}

class AsyncEditorLoader internal constructor(private val textEditor: TextEditorImpl,
                                             private val editorComponent: TextEditorComponent,
                                             private val provider: TextEditorProvider,
                                             private val coroutineScope: CoroutineScope) {
  private val editor: Editor
    get() = textEditor.editor

  private val project: Project
    get() = textEditor.project

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

  @RequiresEdt
  internal fun start() {
    editor.putUserData(ASYNC_LOADER, this)

    if (ApplicationManager.getApplication().isUnitTestMode) {
      val continuation = runBlockingModal(project, "") {
        loadEditor()
      }
      editor.putUserData(ASYNC_LOADER, null)
      continuation.run()
      while (true) {
        (delayedActions.pollFirst() ?: break).run()
      }
    }
    else {
      val continuationDeferred = coroutineScope.async {
        loadEditor()
      }

      // do not show half-ready editor (not highlighted)
      editorComponent.editor.component.isVisible = false

      val modality = ModalityState.current().asContextElement()
      val indicatorJob = editorComponent.loadingDecorator.startLoading(scope = coroutineScope + modality,
                                                                       addUi = editorComponent::addLoadingDecoratorUi)

      coroutineScope.launch(modality) {
        val continuation = continuationDeferred.await()
        editorComponent.loadingDecorator.stopLoading(scope = this, indicatorJob = indicatorJob)
        loaded(continuation)
        EditorNotifications.getInstance(project).updateNotifications(textEditor.file)
      }
    }
  }

  private suspend fun loaded(continuation: Runnable) {
    withContext(Dispatchers.EDT) {
      editor.putUserData(ASYNC_LOADER, null)

      LOG.runAndLogException {
        continuation.run()
      }

      ensureActive()

      // should be before executing delayed actions - editor state restoration maybe a delayed action, and it uses `doWhenFirstShown`,
      // for performance reasons better to avoid
      editorComponent.editor.component.isVisible = true

      editor.scrollingModel.disableAnimation()
      while (true) {
        (delayedActions.pollFirst() ?: break).run()
        ensureActive()
      }
      editor.scrollingModel.enableAnimation()
    }
  }

  private suspend fun loadEditor(): Runnable {
    return constrainedReadAction(ReadConstraint.withDocumentsCommitted(project)) { textEditor.loadEditorInBackground() }
  }

  @RequiresReadLock
  fun getEditorState(level: FileEditorStateLevel): TextEditorState {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    return provider.getStateImpl(project, editor, level)
  }

  @RequiresEdt
  fun setEditorState(state: TextEditorState, exactState: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    provider.setStateImpl(project, editor, state, exactState)
  }

  internal fun dispose() {
    coroutineScope.cancel()
  }
}