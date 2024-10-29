// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.highlighting.fixture

import com.intellij.codeHighlighting.TextEditorHighlightingPassRegistrar
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.impl.EditorTracker
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds

private val LOG = Logger.getInstance("#com.intellij.testframework.junit5.highlighting")

private val HIGHLIGHTING_KEY = Key.create<HighlightingState>("highlighting state for junit5 tests")

@TestOnly
fun TestFixture<Editor>.highlightingFixture(
): TestFixture<DaemonCodeAnalyzer> = testFixture { _ ->
  val editor = this@highlightingFixture.init()
  val editorTrackerCleanup = configureEditorTracker(editor)
  val (analyzer, analyzerCleanup) = configureDaemonCodeAnalyzer(editor)
  initialized(analyzer) {
    analyzerCleanup()
    editorTrackerCleanup()
  }
}

@TestOnly
private suspend fun configureEditorTracker(editor: Editor): suspend () -> Unit {
  val project = requireNotNull(editor.project)
  val editorTracker = project.serviceAsync<EditorTracker>()
  val previousEditors = editorTracker.activeEditors
  withContext(Dispatchers.EDT) {
    project.serviceAsync<EditorTracker>().activeEditors = previousEditors + editor
  }
  return {
    withContext(Dispatchers.EDT) {
      editorTracker.activeEditors = previousEditors
    }
  }
}

private suspend fun configureDaemonCodeAnalyzer(editor: Editor): Pair<DaemonCodeAnalyzer, () -> Unit> {
  val project = requireNotNull(editor.project)
  val daemonCodeAnalyzer = project.serviceAsync<DaemonCodeAnalyzer>()
  // preloading emulation
  project.serviceAsync<TextEditorHighlightingPassRegistrar>()
  DaemonCodeAnalyzerSettings.getInstance().autoReparseDelay = 10
  val disposable = Disposer.newDisposable()
  val connection = project.messageBus.connect(disposable)
  editor.putUserData(HIGHLIGHTING_KEY, HighlightingState(isActive = false, waiters = CopyOnWriteArrayList()))
  connection.subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, TestDaemonCodeAnalyzerListener(editor))
  return daemonCodeAnalyzer to {
    Disposer.dispose(disposable)
  }
}

private class TestDaemonCodeAnalyzerListener(val editor: Editor) : DaemonCodeAnalyzer.DaemonListener {
  override fun daemonStarting(fileEditors: Collection<FileEditor>) {
    val state = editor.extractHighlightingState(fileEditors)
    state.isActive = true
  }

  override fun daemonFinished(fileEditors: Collection<FileEditor>) {
    val state = editor.extractHighlightingState(fileEditors)
    state.waiters.forEach {
      it.resumeWith(Result.success(Unit))
    }
    state.waiters.clear()
    state.isActive = false
  }
}

private fun Editor.extractHighlightingState(fileEditors: Collection<FileEditor>): HighlightingState {
  if (FileEditorManager.getInstance(project!!).getEditors(virtualFile).intersect(fileEditors).isEmpty()) {
    LOG.warn("The tested editor is not among highlighted ones: $this; $fileEditors")
  }
  val state = requireNotNull(getUserData(HIGHLIGHTING_KEY)) {
    "Internal error: Could not find highlighting key for $this"
  }
  return state
}

private data class HighlightingState(@Volatile var isActive: Boolean, val waiters: MutableList<CancellableContinuation<Unit>>)

@TestOnly
suspend fun Editor.awaitHighlighting() {
  val project = project
  requireNotNull(project) {
    "Active project is required to await highlighting"
  }
  requireNotNull(project.getServiceIfCreated(DaemonCodeAnalyzer::class.java)) {
    "DaemonCodeAnalyzer must be created before highlighting can be awaited.\n" +
    "Probably you forgot to add highlightingFixture() to your test fixture."
  }
  withContext(Dispatchers.EDT) {
    // DaemonCodeAnalyzer likes to run on EDT sometimes,
    // so here we are delaying ourselves until the event queue is processed.
    // todo: times(2) is a safety net. There should be a way to remove it and wait for highlighting to start more reliably
    delay(DaemonCodeAnalyzerSettings.getInstance().autoReparseDelay.times(2).milliseconds)
  }
  val state = getUserData(HIGHLIGHTING_KEY)!!
  if (!state.isActive) {
    return
  }
  suspendCancellableCoroutine { continuation ->
    state.waiters.add(continuation)
  }
}