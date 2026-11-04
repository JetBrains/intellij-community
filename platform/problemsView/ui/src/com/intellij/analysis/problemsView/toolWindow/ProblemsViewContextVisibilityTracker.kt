// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SplitModeApiUsage")

package com.intellij.analysis.problemsView.toolWindow

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextChangeListener
import com.intellij.codeInsight.multiverse.CodeInsightContextManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.problemsView.shared.ProblemsViewCoroutineScopeHolder
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration.Companion.milliseconds

/**
 * Keeps the [ProblemsViewContextVisibility] of one file up to date.
 *
 * The daemon, the editor selection and the contexts of the file send update requests.
 * The tracker computes the visibility after the requests stop for [UPDATE_DELAY].
 * A new request cancels the computation of an old one.
 * The tracker reads the file facts again only after a change of the contexts of the file.
 */
internal class ProblemsViewContextVisibilityTracker(
  private val project: Project,
  private val file: VirtualFile,
  private val document: Document,
  parentDisposable: Disposable,
  /** Called on the EDT after the tracker computes the visibility. */
  private val visibilityUpdated: () -> Unit,
) : Disposable {
  /**
   * The visibility of the contexts of the file, or `null` when the file has one context. Then the view shows every context.
   * Before the first update, the value is [ProblemsViewContextVisibility.provisional].
   * The EDT writes the value, and a query reads it on any thread.
   */
  @Volatile
  var visibility: ProblemsViewContextVisibility? = ProblemsViewContextVisibility.provisional(project, document)
    private set

  private val cs: CoroutineScope =
    ProblemsViewCoroutineScopeHolder.getInstance().createNamedChildScope("ProblemsViewContextVisibilityTracker")

  // a state flow conflates the requests that arrive before the collector takes them
  private val request = MutableStateFlow(UpdateRequest(id = 0, fileFactsId = 0))
  private val appliedRequestId = MutableStateFlow(0L)

  // collectLatest runs one update at a time, so the updates share the cache without a lock
  private var cachedFileFacts: FileFacts? = null
  private var cachedFileFactsId = 0L

  init {
    Disposer.register(parentDisposable, this)
    subscribe()
    cs.launch { updateVisibility() }
    requestUpdate(fileFacts = true)
  }

  override fun dispose() {
    cs.cancel()
  }

  private fun subscribe() {
    val connection = project.messageBus.connect(this)
    // an edit and a switch of the editor context restart the daemon
    connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
      override fun daemonStarting(fileEditors: Collection<FileEditor>) = requestUpdate(fileEditors)
      override fun daemonFinished(fileEditors: Collection<FileEditor>) = requestUpdate(fileEditors)
    })
    // split editors of one file can show different contexts, and the context of the selected editor comes first
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        if (event.oldFile == file || event.newFile == file) requestUpdate(fileFacts = false)
      }
    })
    connection.subscribe(CodeInsightContextManager.topic, object : CodeInsightContextChangeListener {
      override fun contextsChanged() = requestUpdate(fileFacts = true)

      override fun contextsChanged(files: Set<VirtualFile>) {
        if (file in files) requestUpdate(fileFacts = true)
      }
    })
  }

  private fun requestUpdate(fileEditors: Collection<FileEditor>) {
    if (fileEditors.any { it.file == file }) {
      requestUpdate(fileFacts = false)
    }
  }

  private fun requestUpdate(fileFacts: Boolean) {
    request.update { UpdateRequest(id = it.id + 1, fileFactsId = if (fileFacts) it.id + 1 else it.fileFactsId) }
  }

  @OptIn(FlowPreview::class)
  private suspend fun updateVisibility() {
    request
      .filter { it.id > 0 }
      .debounce(UPDATE_DELAY)
      .collectLatest { request ->
        // a failure of one request must not stop the updates of the next requests
        LOG.runAndLogException { update(request) }
        appliedRequestId.value = request.id
      }
  }

  private suspend fun update(request: UpdateRequest) {
    (val hasSeveralContexts, val supportsSimultaneousRendering, val preferredContext) = fileFacts(request.fileFactsId)
    // with one context, the visibility stays null, and the view has nothing to update
    if (!hasSeveralContexts && visibility == null) return
    withContext(Dispatchers.EDT) {
      visibility = when {
        hasSeveralContexts -> ProblemsViewContextVisibility.compute(project, document, supportsSimultaneousRendering, preferredContext)
        else -> null
      }
      visibilityUpdated()
    }
  }

  /** Reads the file facts again when the request [id] is newer than the cached facts. */
  private suspend fun fileFacts(id: Long): FileFacts {
    cachedFileFacts?.takeIf { cachedFileFactsId == id }?.let { return it }
    return readAction { computeFileFacts() }.also {
      cachedFileFacts = it
      cachedFileFactsId = id
    }
  }

  @RequiresReadLock
  private fun computeFileFacts(): FileFacts {
    val contextManager = CodeInsightContextManager.getInstance(project)
    val supportsSimultaneousRendering = ProblemsViewContextVisibility.supportsSimultaneousRendering(project, file)
    return FileFacts(
      hasSeveralContexts = contextManager.getCodeInsightContexts(file).size > 1,
      supportsSimultaneousRendering = supportsSimultaneousRendering,
      // the context that a new editor of the file gets
      preferredContext = if (supportsSimultaneousRendering) null else contextManager.getPreferredContext(file),
    )
  }

  /** Waits until the tracker applies the last request. */
  @TestOnly
  suspend fun awaitUpdate() {
    val id = request.value.id
    appliedRequestId.first { it >= id }
  }
}

private val LOG = logger<ProblemsViewContextVisibilityTracker>()

private val UPDATE_DELAY = 100.milliseconds

/** @property fileFactsId the [id] of the last request that asks to read the file facts again. */
private data class UpdateRequest(val id: Long, val fileFactsId: Long)

/** The facts of the file that the context rule needs. A read action computes them. */
private data class FileFacts(
  val hasSeveralContexts: Boolean,
  /** `false` when the provider of the file does not support simultaneous rendering. */
  val supportsSimultaneousRendering: Boolean,
  /** The context that the view shows without an editor context. Set only without simultaneous rendering. */
  val preferredContext: CodeInsightContext?,
)
