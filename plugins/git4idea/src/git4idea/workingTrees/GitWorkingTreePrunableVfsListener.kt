// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Listens to file system events and schedules reload of a working trees panel if needed.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class GitWorkingTreePrunableVfsListener(private val project: Project, coroutineScope: CoroutineScope): AsyncVfsEventsListener {
  init {
    AsyncVfsEventsPostProcessor.getInstance().addListener(this, coroutineScope)
  }

  override suspend fun filesChanged(events: List<VFileEvent>) {
    val repo = GitWorkingTreesService.getRepoForWorkingTreesSupport(project) ?: return
    val workingTreesPaths = repo.workingTreeHolder.getWorkingTrees().map { it.path }
    val deletedPaths = events.filterIsInstance<VFileDeleteEvent>().filter { it.file.isDirectory }.mapTo(HashSet()) { it.path }

    val isAnyWorkingTreeAffected = workingTreesPaths.any { wtPath ->
      generateSequence(wtPath.path) { path ->
        path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }
      }.any { it in deletedPaths }
    }

    if (isAnyWorkingTreeAffected) {
      repo.workingTreeHolder.scheduleReload()
    }
  }
}
