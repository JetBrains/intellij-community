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

  private val DIALOG_CLOSED = GROUP.registerVarargEvent("dialog.closed", ANSWER, DIFFERENCE_SHOWN, CAUSE, EventFields.FileType)

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

  /**
   * Why the conflict dialog appears. The [CAUSE] field describes each value.
   */
  enum class DialogCause {
    AUTOMERGE_OFF,
    AUTOMERGE_FAILED,
  }

  private enum class DialogAnswer {
    LOAD_FS_CHANGES,
    KEEP_MEMORY_CHANGES,
  }
}
