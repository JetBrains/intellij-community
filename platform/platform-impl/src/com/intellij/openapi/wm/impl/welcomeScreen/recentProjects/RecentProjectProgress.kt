// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.CommonBundle
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService.CloneableProject
import com.intellij.platform.ide.progress.CancellableTaskCancellation
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.TaskHandle
import com.intellij.platform.ide.progress.TaskId
import com.intellij.platform.util.progress.ProgressState
import com.intellij.util.text.nullize
import java.nio.file.Path

/** Tells one operation of a welcome screen row from another. It stays the same while the operation runs. */
internal sealed interface ProgressId {
  @JvmInline
  value class Task(val taskId: TaskId) : ProgressId

  @JvmInline
  value class Clone(val projectPath: Path) : ProgressId
}

/**
 * What one operation of a welcome screen row shows now.
 *
 * A value, not a handle: the window renders it and reads nothing through it. The window keys its rows on [id], so a later value only
 * repaints the row it belongs to, and cancelling one goes back to [RecentProjectProgressTracker.cancel] with that id.
 */
internal class RecentProjectProgress(
  val id: ProgressId,
  val title: @ProgressTitle String,
  val text: @ProgressText String?,
  val fraction: Double?,
  val isCancellable: Boolean,
  val cancelTooltip: @NlsContexts.Tooltip String,
  /** What to ask before this is cancelled, or null to cancel at once. */
  val cancelQuestion: @DialogMessage String? = null,
  /** The title of [cancelQuestion], or null to ask under [title]. */
  val cancelQuestionTitle: @DialogTitle String? = null,
)

/** What this task shows, given the [state] it last reported. */
internal fun TaskHandle.toProgress(state: ProgressState?): RecentProjectProgress {
  val info = info
  return RecentProjectProgress(
    id = ProgressId.Task(info.id),
    title = info.title,
    text = state?.text?.nullize(nullizeSpaces = true),
    fraction = state?.fraction,
    isCancellable = info.cancellation is TaskCancellation.Cancellable,
    cancelTooltip = (info.cancellation as? CancellableTaskCancellation)?.tooltipText ?: CommonBundle.getCancelButtonText(),
  )
}

/** What the clone of this project shows now. It reports into an indicator of its own, which is read here. */
internal fun CloneableProject.toProgress(): RecentProjectProgress {
  val info = cloneTaskInfo
  return RecentProjectProgress(
    id = ProgressId.Clone(projectPath),
    title = info.title,
    text = progressIndicator.text,
    fraction = progressIndicator.fraction.takeUnless { progressIndicator.isIndeterminate },
    isCancellable = true,
    cancelTooltip = info.cancelTooltipText,
    // Stopping a clone throws away what it downloaded, which the row's cancel button asked about before the window took its place.
    cancelQuestion = info.stopDescription,
    cancelQuestionTitle = info.stopTitle,
  )
}

/**
 * What every recent project the welcome screen holds is running, in [ROW_ORDER].
 *
 * It answers for an item rather than handing out its map, because a project is keyed by its path: a [RecentProjectItem] is a value the
 * tree rebuilds on every refresh, and its branch name and its activation time change under the same project.
 */
internal class ProjectsProgress(private val byProject: Map<Path, List<RecentProjectProgress>>) {
  /** What the recent project of [item] is running, empty when it runs nothing. */
  fun getProgressOf(item: RecentProjectItem): List<RecentProjectProgress> = item.projectNioPath()?.let(byProject::get).orEmpty()

  companion object {
    val NONE: ProjectsProgress = ProjectsProgress(emptyMap())
  }
}
