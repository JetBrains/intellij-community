// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

private val log = logger<XLineBreakpointRemovalUndoableAction>()

private class RemovedBreakpointRecord(
  var breakpointId: XBreakpointId,
  val line: Int,
  /** Pending request to remove a breakpoint so that restore doesn't start concurrently */
  var pending: Job,
)

internal class XLineBreakpointRemovalUndoableAction private constructor(
  private val manager: FrontendXBreakpointManager,
  private val cs: CoroutineScope,
  private val records: List<RemovedBreakpointRecord>,
  private val document: Document,
) : BasicUndoableAction(document) {

  override fun undo() {
    // We must ensure that the backend updates the breakpoint position first,
    // and only then attempts to restore a breakpoint. Otherwise the restored breakpoint
    // may overwrite the breakpoint at the same position.
    //
    // Consider the following case:
    //
    // fun foo() {
    //   val a = "A"; // Breakpoint A
    //   val b = "B"; // Breakpoint B
    // }
    //
    // When line a is removed (together with Breakpoint A), the same logical line
    // gets occupied with the line b (and Breakpoint B).
    // Undoing the removal must ensure that the Breakpoint B goes back to its original position
    // *before* the Breakpoint A restores.
    // Hence drainRequests.
    val breakpointsUpdated = manager.getLineBreakpointManager().getDocumentBreakpointProxies(document)
      .mapNotNull { (it as? FrontendXLineBreakpointProxy)?.drainRequests() }
    for (record in records) {
      record.pending = runAfter(record.pending) {
        breakpointsUpdated.joinAll()
        manager.restoreRemovedBreakpoint(record.breakpointId, record.line)?.let { record.breakpointId = it }
      }
    }
  }

  override fun redo() {
    for (record in records) {
      record.pending = runAfter(record.pending) { manager.removeBreakpointUndoable(record.breakpointId) }
    }
  }

  private fun runAfter(previous: Job, task: suspend () -> Unit): Job = cs.launch {
    previous.join()
    try {
      task()
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      log.warn("Breakpoint undo step failed", e)
    }
  }

  companion object {
    /**
     * Removes [breakpoints] now and registers the undo step for them in the current command.
     * Returns `false` when no undo step can be registered. Then nothing is removed.
     */
    fun removeWithUndo(
      project: Project,
      manager: FrontendXBreakpointManager,
      cs: CoroutineScope,
      document: Document,
      breakpoints: List<FrontendXLineBreakpointProxy>,
    ): Boolean {
      if (!EDT.isCurrentThreadEdt()) return false
      if (CommandProcessor.getInstance().currentCommandProject != project) return false
      val undoManager = UndoManager.getInstance(project)
      if (undoManager.isUndoOrRedoInProgress) return false
      val records = breakpoints.map { RemovedBreakpointRecord(it.id, it.getLine(), manager.removeBreakpointUndoable(it).asDeferred()) }
      undoManager.undoableActionPerformed(XLineBreakpointRemovalUndoableAction(manager, cs, records, document))
      return true
    }
  }
}
