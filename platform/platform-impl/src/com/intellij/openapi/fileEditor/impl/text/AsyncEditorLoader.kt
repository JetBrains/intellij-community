// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.impl.EditorsSplitters.Companion.isOpenedInBulk
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.EditorNotifications
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class AsyncEditorLoader internal constructor(private val textEditor: TextEditorImpl,
                                             private val editorComponent: TextEditorComponent,
                                             private val provider: TextEditorProvider) {
  private val editor: Editor = textEditor.editor
  private val project: Project = textEditor.myProject
  private val delayedActions = ArrayList<Runnable>()
  private var delayedState: TextEditorState? = null
  private val loadingFinished = AtomicBoolean()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val dispatcher = Dispatchers.Default.limitedParallelism(2)

  init {
    editor.putUserData(ASYNC_LOADER, this)
    editorComponent.editor.component.isVisible = false
  }

  companion object {
    private val ASYNC_LOADER = Key.create<AsyncEditorLoader>("ASYNC_LOADER")
    private const val SYNCHRONOUS_LOADING_WAITING_TIME_MS = 200
    private const val DOCUMENT_COMMIT_WAITING_TIME_MS = 5000L
    private val LOG = Logger.getInstance(AsyncEditorLoader::class.java)

    private fun <T> resultInTimeOrNull(future: CompletableFuture<T>): T? {
      try {
        return future.get(SYNCHRONOUS_LOADING_WAITING_TIME_MS.toLong(), TimeUnit.MILLISECONDS)
      }
      catch (ignored: InterruptedException) {
      }
      catch (ignored: TimeoutException) {
      }
      return null
    }

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
  fun start() {
    val asyncLoading = project.coroutineScope.async(dispatcher) {
      doLoad()
    }
    asyncLoading.cancelOnDispose(editorComponent)

    // we can't return the result of async call because it's only finished on EDT later,
    // but we need to get the result of bg calculation in the same EDT event, if it's quick
    if (worthWaiting()) {
      /*
       * Possible alternatives:
       * 1. show "Loading" from the beginning, then it'll be always noticeable at least in fade-out phase
       * 2. show a gray screen for some time and then "Loading" if it's still loading; it'll produce quick background blinking for all editors
       * 3. show non-highlighted and unfolded editor as "Loading" background and allow it to relayout at the end of loading phase
       * 4. freeze EDT a bit and hope that for small editors it'll suffice and for big ones show "Loading" after that.
       * This strategy seems to produce minimal blinking annoyance.
       */
      resultInTimeOrNull(asyncLoading.asCompletableFuture())?.let {
        loadingFinished(it)
        return
      }
    }

    editorComponent.startLoading()
    project.coroutineScope.async(dispatcher) {
      val continuation = asyncLoading.await()
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        loadingFinished(continuation)
      }
    }
  }

  private suspend fun doLoad(): Runnable? {
    waitForCommit()

    try {
      return readAction { textEditor.loadEditorInBackground() }
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: IndexOutOfBoundsException) {
      // EA-232290 investigation
      val filePathAttachment = Attachment("filePath.txt", textEditor.file.toString())
      val threadDumpAttachment = Attachment("threadDump.txt", ThreadDumper.dumpThreadsToString())
      threadDumpAttachment.isIncluded = true
      LOG.error("Error during async editor loading", e, filePathAttachment, threadDumpAttachment)
      return null
    }
    catch (e: Exception) {
      LOG.error("Error during async editor loading", e)
      return null
    }
  }

  private suspend fun waitForCommit() {
    val document = editor.document
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    if (psiDocumentManager.isCommitted(document)) {
      return
    }

    val deferred = CompletableDeferred<Unit>()
    psiDocumentManager.performForCommittedDocument(document) { deferred.complete(Unit) }
    withTimeoutOrNull(DOCUMENT_COMMIT_WAITING_TIME_MS) {
      deferred.join()
    }
  }

  private val isDone: Boolean
    get() = loadingFinished.get()

  private fun worthWaiting(): Boolean {
    // cannot perform commitAndRunReadAction in parallel to EDT waiting
    return !isOpenedInBulk(textEditor.myFile) &&
           !PsiDocumentManager.getInstance(project).hasUncommitedDocuments() &&
           !ApplicationManager.getApplication().isWriteAccessAllowed
  }

  private fun loadingFinished(continuation: Runnable?) {
    if (!loadingFinished.compareAndSet(false, true)) {
      return
    }
    editor.putUserData(ASYNC_LOADER, null)
    if (editorComponent.isDisposed) {
      return
    }
    if (continuation != null) {
      try {
        continuation.run()
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }
    editorComponent.loadingFinished()
    if (delayedState != null && PsiDocumentManager.getInstance(project).isCommitted(editor.document)) {
      val state = TextEditorState()
      state.RELATIVE_CARET_POSITION = Int.MAX_VALUE // don't do any scrolling
      state.foldingState = delayedState!!.foldingState
      provider.setStateImpl(project, editor, state, true)
      delayedState = null
    }
    for (runnable in delayedActions) {
      editor.scrollingModel.disableAnimation()
      runnable.run()
    }
    delayedActions.clear()
    editor.scrollingModel.enableAnimation()
    EditorNotifications.getInstance(project).updateNotifications(textEditor.myFile)
  }

  fun getEditorState(level: FileEditorStateLevel): TextEditorState {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val state = provider.getStateImpl(project, editor, level)
    val delayedState = delayedState
    if (!isDone && delayedState != null) {
      state.setDelayedFoldState { delayedState.foldingState }
    }
    return state
  }

  fun setEditorState(state: TextEditorState, exactState: Boolean) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (!isDone) {
      delayedState = state
    }
    provider.setStateImpl(project, editor, state, exactState)
  }
}