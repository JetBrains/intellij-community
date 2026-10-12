// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.fileEditor.impl.MemoryDiskConflictResolver.DialogResult
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile

/**
 * Reports how a conflict between the unsaved changes of a document and a change of its file on disk ends.
 */
internal object FileCacheConflictCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("file.cache.conflict", 1)

  private val ANSWER = EventFields.Enum<DialogAnswer>(
    "answer",
    "The button that closed the dialog. Esc and 'Save Memory Content' in the difference view give KEEP_MEMORY_CHANGES",
  )
  private val DIFFERENCE_SHOWN = EventFields.Boolean(
    "difference_shown",
    "Whether the user opened the difference view before closing the dialog",
  )
  private val CAUSE = EventFields.Enum<DialogCause>(
    "cause",
    "AUTOMERGE_OFF when no client asked to merge external changes, for example when no MCP session was open. " +
    "AUTOMERGE_FAILED when a client asked, but the merge did not apply. The value is taken when the dialog appears",
  )

  private val RESULT = EventFields.Enum<AutomergeResult>(
    "result",
    "MERGED when the merge applied. Otherwise the reason it did not. " +
    "NOT_FROM_REFRESH: code in the IDE wrote the file, so nothing read the new content in advance. " +
    "NOT_PREFETCHED: a refresh did not read the new content in advance. The file is binary, the file or the batch is too large, " +
    "the file type changes, the file changed during the read, or the read failed. " +
    "NO_COMMON_TEXT: the document has no text in common with the file. " +
    "CONFLICTING_CHANGES: the two sets of changes overlap, or they are too big to compare. " +
    "NOT_APPLIED: the merge was computed, but the document or the file changed, the document is read-only, or the reload failed",
  )

  private val DIALOG_CLOSED = GROUP.registerVarargEvent("dialog.closed", ANSWER, DIFFERENCE_SHOWN, CAUSE, EventFields.FileType)
  private val AUTOMERGE_FINISHED = GROUP.registerEvent("automerge.finished", RESULT, EventFields.FileType)

  override fun getGroup(): EventLogGroup = GROUP

  @JvmStatic
  fun logDialogClosed(file: VirtualFile, cause: DialogCause, result: DialogResult) {
    val answer = if (result.loadFsChanges) DialogAnswer.LOAD_FS_CHANGES else DialogAnswer.KEEP_MEMORY_CHANGES
    DIALOG_CLOSED.log(
      ProjectLocator.getInstance().guessProjectForFile(file),
      ANSWER.with(answer),
      DIFFERENCE_SHOWN.with(result.differenceShown),
      CAUSE.with(cause),
      EventFields.FileType.with(file.fileType),
    )
  }

  @JvmStatic
  fun logAutomergeFinished(file: VirtualFile, result: AutomergeResult) {
    AUTOMERGE_FINISHED.log(ProjectLocator.getInstance().guessProjectForFile(file), result, file.fileType)
  }

  /**
   * Why the conflict dialog appears. The [CAUSE] field describes each value.
   */
  enum class DialogCause {
    AUTOMERGE_OFF,
    AUTOMERGE_FAILED,
  }

  /**
   * How the merge of one conflict ends, when a client asked to merge the external changes. The [RESULT] field describes each value.
   */
  enum class AutomergeResult {
    MERGED,
    NOT_FROM_REFRESH,
    NOT_PREFETCHED,
    NO_COMMON_TEXT,
    CONFLICTING_CHANGES,
    NOT_APPLIED,
  }

  private enum class DialogAnswer {
    LOAD_FS_CHANGES,
    KEEP_MEMORY_CHANGES,
  }
}
