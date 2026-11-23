// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.history.LocalHistory
import com.intellij.ide.GeneralSettings
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.FutureVirtualFile
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.modcommand.ModCreateFile
import com.intellij.modcommand.ModDeleteFile
import com.intellij.modcommand.ModMoveFile
import com.intellij.modcommand.ModUpdateFileText
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.listeners.RefactoringListenerManager
import com.intellij.refactoring.listeners.impl.RefactoringListenerManagerImpl
import com.intellij.refactoring.rename.RenameUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.io.TrashBin
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

/** The outcome of [ModPlanExecutor.execute]. */
sealed interface ModPlanExecutionResult {
  /** All writes were made. */
  data object Applied : ModPlanExecutionResult

  /** A file changed since the analysis; nothing was written. */
  data class Stale(val files: List<VirtualFile>) : ModPlanExecutionResult

  /** A file is read-only; nothing was written. */
  data class ReadOnly(val files: List<VirtualFile>) : ModPlanExecutionResult

  /**
   * The writes stopped part way: some of [ModPlanExecution.affectedFiles] may be written.
   * What was written is one undo step, as after [Applied].
   */
  data class Failed(val message: @Nls String) : ModPlanExecutionResult
}

/**
 * Applies a [ModPlanExecution] the way `BaseRefactoringProcessor` applies a refactoring: in one command with one global undo
 * step, with a local history label and refactoring events, but without any UI. The writes are all-or-nothing against stale or read-only
 * files.
 */
object ModPlanExecutor {
  /**
   * Writes the files of [execution]. If a file changed since the analysis or is read-only, nothing is written and no event is
   * published. No `RefactoringHelper` (such as import optimization) runs here: the [ModPlanRefactoringListener]s ran in the plan.
   * Whatever was written, also by a failed execution, is one undo step. A file deletion is undoable only where local history runs, as in an interactive refactoring.
   * The refactoring events have no data, and the refactoring listeners hear about no element: to tell them, pass
   * [ModPlan.AcceptedPlan.writeEvents] to the other overload.
   *
   * @param globalUndo whether the undo step is global, as it has to be if no editor is involved. An interactive refactoring that the user
   * started in an editor passes false, so that the undo in that editor needs no confirmation.
   */
  @JvmStatic
  @RequiresEdt
  fun execute(execution: ModPlanExecution, globalUndo: Boolean): ModPlanExecutionResult {
    return execute(execution, globalUndo) { ModPlanRefactoring.WriteEvent.DEFAULT }
  }

  /**
   * Writes the files of [execution], see [execute], with the events of [writeEvents], which runs in the command, before the writes: the
   * refactoring events get its data, and the refactoring listeners hear about its element changes after the writes. Pass
   * [ModPlan.AcceptedPlan.writeEvents].
   */
  @JvmStatic
  @RequiresEdt
  fun execute(execution: ModPlanExecution, globalUndo: Boolean, writeEvents: Supplier<ModPlanRefactoring.WriteEvent>): ModPlanExecutionResult {
    return WriteIntentReadAction.compute { run(execution, globalUndo, writeEvents) }
  }

  private fun run(execution: ModPlanExecution, globalUndo: Boolean, writeEvents: Supplier<ModPlanRefactoring.WriteEvent>): ModPlanExecutionResult {
    val project = execution.project
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    if (execution.writes.isEmpty()) {
      return ModPlanExecutionResult.Applied
    }

    val staleFiles = staleFiles(execution)
    if (staleFiles.isNotEmpty()) {
      return ModPlanExecutionResult.Stale(staleFiles.toList())
    }
    // The existing files of the execution that cannot be written: the file or its loaded document is read-only.
    val documents = FileDocumentManager.getInstance()
    val readOnlyFiles = execution.writtenFilesAndDirectories
      .filter { !it.isWritable || documents.getCachedDocument(it)?.isWritable == false }
    if (readOnlyFiles.isNotEmpty()) {
      return ModPlanExecutionResult.ReadOnly(readOnlyFiles)
    }

    val psiManager = PsiManager.getInstance(project)
    // A plan that only creates and moves files has no file to give the context of the batch.
    val contextFile = execution.writes.filter { it !is ModCreateFile }.firstNotNullOfOrNull { write ->
      ModPlanExecution.writtenFile(write).takeIf { !it.isDirectory }?.let { psiManager.findFile(it) }
    } ?: PsiFileFactory.getInstance(project).createFileFromText("refactoring.txt", PlainTextFileType.INSTANCE, "")
    var events: ModPlanRefactoring.WriteEvent? = null
    var batch: ModCommandExecutor.BatchExecutionResult? = null
    CommandProcessor.getInstance().executeCommand(project, {
      val history = LocalHistory.getInstance().startAction(execution.commandName)
      val trashedFiles = toTrash(execution)
      try {
        trashedFiles.forEach { LocalFileSystem.MOVE_TO_TRASH.set(it, true) }
        val currentEvents = writeEvents.get()
        events = currentEvents
        project.messageBus.syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
          .refactoringStarted(execution.refactoringId, currentEvents.beforeData())
        UndoManager.getInstance(project).undoableActionPerformed(UndoRefactoringAction(project, execution.refactoringId))
        if (globalUndo) {
          CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
        }
        // As in BaseRefactoringProcessor: the providers get the elements before the writes, the listeners run when the transaction commits.
        // As in RenameProcessor, the undo of the rename tells the listeners the old name.
        val transaction = (RefactoringListenerManager.getInstance(project) as RefactoringListenerManagerImpl).startTransaction()
        val listeners = currentEvents.elementChanges().map { change ->
          val listener = transaction.getElementListener(change.before())
          if (change.kind() == ModPlanRefactoring.ElementChange.Kind.RENAMED) {
            RenameUtil.registerUndoableRename(change.before(), listener)
          }
          change to listener
        }
        val currentBatch = ModCommandExecutor.getInstance().executeInBatch(ActionContext.from(null, contextFile), execution.command)
        batch = currentBatch
        if (currentBatch == ModCommandExecutor.Result.SUCCESS) {
          WriteAction.run<RuntimeException> { currentEvents.afterWrites().run() }
          if (listeners.isNotEmpty()) DumbService.getInstance(project).completeJustSubmittedTasks()
          // As in BaseRefactoringProcessor, the listeners run after the write action, so a listener may show a dialog.
          notify(listeners)
          transaction.commit()
        }
      }
      finally {
        trashedFiles.forEach { LocalFileSystem.MOVE_TO_TRASH.set(it, null) }
        // As in BaseRefactoringProcessor: the end is published only after the start, and the local history action always finishes.
        try {
          events?.let { startedEvents ->
            project.messageBus.syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC)
              .refactoringDone(execution.refactoringId, startedEvents.afterData().get())
          }
        }
        finally {
          history.finish()
        }
      }
    }, execution.commandName, null, UndoConfirmationPolicy.DEFAULT, false)

    val result = batch
    return if (result == ModCommandExecutor.Result.SUCCESS) {
      ModPlanExecutionResult.Applied
    }
    else {
      ModPlanExecutionResult.Failed(result?.message ?: "")
    }
  }

  /**
   * Tells the listeners of the transaction about the elements after the writes, as the legacy rename does: an element that is not found
   * is skipped. The transaction gives them to the real listeners when it commits.
   */
  private fun notify(listeners: List<Pair<ModPlanRefactoring.ElementChange, RefactoringElementListener>>) {
    for ([change, listener] in listeners) {
      val element = change.after().get() ?: continue
      when (change.kind()) {
        ModPlanRefactoring.ElementChange.Kind.RENAMED -> listener.elementRenamed(element)
        ModPlanRefactoring.ElementChange.Kind.MOVED -> listener.elementMoved(element)
      }
    }
  }

  /**
   * The deleted files that go to the trash: if [ModPlanExecution.trashDeletedFiles] is set, the trash is supported and the user deletes
   * to it. A directory is deleted.
   */
  private fun toTrash(execution: ModPlanExecution): List<VirtualFile> {
    if (!execution.trashDeletedFiles || !TrashBin.isSupported() || !GeneralSettings.getInstance().isDeletingToBin) return emptyList()
    return execution.writes.filterIsInstance<ModDeleteFile>().map { it.file() }.filter { !it.isDirectory && TrashBin.canMoveToTrash(it) }
  }

  /** The files that changed since the analysis, or whose target place is taken. The checks differ by the kind of the write. */
  @ApiStatus.Internal
  fun staleFiles(execution: ModPlanExecution): Set<VirtualFile> {
    val resultFiles = LinkedHashSet<VirtualFile>()
    execution.snapshotFiles.filterTo(resultFiles) { !execution.isUnchanged(it) }
    val createdPaths = HashSet<String>()
    for (write in execution.writes) {
      when (write) {
        is ModUpdateFileText -> {
          val file = write.file()
          val document = if (file.isValid) FileDocumentManager.getInstance().getDocument(file) else null
          if (document == null || !StringUtil.equals(document.immutableCharSequence, write.oldText())) {
            resultFiles.add(file)
          }
        }
        is ModDeleteFile -> {
          val file = write.file()
          if (!execution.isUnchanged(file)) {
            resultFiles.add(file)
          }
        }
        is ModMoveFile -> {
          val source = write.file()
          if (!execution.isUnchanged(source) || !canPlace(write.targetFile(), createdPaths, source)) {
            resultFiles.add(source)
          }
          createdPaths.add(ModPlanExecution.pathOf(write.targetFile()))
        }
        is ModCreateFile -> {
          val target = write.file()
          if (!canPlace(target, createdPaths, null)) {
            resultFiles.add(ModPlanExecution.existingAncestor(target))
          }
          createdPaths.add(ModPlanExecution.pathOf(target))
        }
        else -> throw IllegalStateException("Not a file write: $write")
      }
    }
    return resultFiles
  }

  /** Whether [target] can be a new child: its parent exists or is created earlier, and nothing but [source] is there already. */
  private fun canPlace(target: FutureVirtualFile, createdPaths: Set<String>, source: VirtualFile?): Boolean {
    val parent = target.parent
    if (parent is FutureVirtualFile) {
      return ModPlanExecution.pathOf(parent) in createdPaths && ModPlanExecution.pathOf(target) !in createdPaths
    }
    if (parent == null || !parent.isValid || !parent.isDirectory) {
      return false
    }
    val existingFile = parent.findChild(target.name)
    return ModPlanExecution.pathOf(target) !in createdPaths && (existingFile == null || existingFile == source)
  }

  private class UndoRefactoringAction(private val project: Project, private val refactoringId: String) : BasicUndoableAction() {
    override fun undo() {
      project.messageBus.syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).undoRefactoring(refactoringId)
    }

    override fun redo() {
      project.messageBus.syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).redoRefactoring(refactoringId)
    }
  }
}
