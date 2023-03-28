// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.ui.EditorNotifications
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import java.util.*

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
    get() = textEditor.myProject

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

    @JvmStatic
    fun isEditorLoaded(editor: Editor): Boolean {
      return editor.getUserData(ASYNC_LOADER) == null
    }
  }

  @RequiresEdt
  internal fun start() {
    editor.putUserData(ASYNC_LOADER, this)

    val continuationDeferred = coroutineScope.async {
      constrainedReadAction(ReadConstraint.withDocumentsCommitted(project)) { textEditor.loadEditorInBackground() }
    }

    // do not show half-ready editor (not highlighted)
    editorComponent.editor.component.isVisible = false

    coroutineScope.launch(ModalityState.current().asContextElement()) {
      val indicatorJob = editorComponent.loadingDecorator.startLoading(scope = this, addUi = editorComponent::addLoadingDecoratorUi)

      val continuation = continuationDeferred.await()
      editorComponent.loadingDecorator.stopLoading(scope = this, indicatorJob = indicatorJob)

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
      EditorNotifications.getInstance(project).updateNotifications(textEditor.myFile)
    }
  }

  @RequiresEdt
  fun getEditorState(level: FileEditorStateLevel): TextEditorState {
    ApplicationManager.getApplication().assertIsDispatchThread()
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