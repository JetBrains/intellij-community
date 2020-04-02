// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.EventDispatcher
import com.intellij.util.messages.MessageBusConnection
import com.intellij.vcs.log.BaseSingleTaskController
import com.intellij.vcs.log.runInEdt
import git4idea.GitVcs
import git4idea.repo.GitRepositoryManager
import git4idea.repo.GitUntrackedFilesHolder
import org.jetbrains.annotations.NotNull
import java.util.*
import kotlin.collections.HashSet

class GitStageTracker(val project: Project) : Disposable {
  private val eventDispatcher = EventDispatcher.create(GitStageTrackerListener::class.java)
  private val singleTaskController = MySingleTaskController()

  @Volatile
  var state: State = State(emptyMap())

  init {
    val connection: MessageBusConnection = project.messageBus.connect(this)
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        val roots = GitRepositoryManager.getInstance(project).repositories.filter { repo ->
          events.any { e -> GitUntrackedFilesHolder.totalRefreshNeeded(repo, e.path) }
        }.map { it.root }
        scheduleUpdateRoots(roots)
      }
    })
    connection.subscribe(VcsDirtyScopeManagerListener.VCS_DIRTY_SCOPE_UPDATED, object : VcsDirtyScopeManagerListener {
      override fun everythingDirty() {
        scheduleUpdateRoots(gitRoots())
      }

      override fun filePathsDirty(filesConverted: Map<VcsRoot, Set<FilePath>>, dirsConverted: Map<VcsRoot, Set<FilePath>>) {
        val roots = filesConverted.keys.union(dirsConverted.keys).filter { it.vcs?.keyInstanceMethod == GitVcs.getKey() }.map { it.path }
        scheduleUpdateRoots(roots)
      }
    })
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsListener {
      if (!scheduleUpdateRoots(gitRoots())) {
        runInEdt(this) {
          update(State(emptyMap()))
        }
      }
    })
  }

  private fun scheduleUpdateRoots(roots: List<@NotNull VirtualFile>): Boolean {
    if (roots.isNotEmpty()) {
      singleTaskController.request(Request(roots))
      return true
    }
    return false
  }

  fun addListener(listener: GitStageTrackerListener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }

  private fun gitRoots(): List<VirtualFile> {
    return ProjectLevelVcsManager.getInstance(project).getRootsUnderVcs(GitVcs.getInstance(project)).toList()
  }

  private fun update(newState: State) {
    this.state = this.state.updatedWith(newState, roots = gitRoots())
    eventDispatcher.multicaster.update()
  }

  override fun dispose() {
    state = State(emptyMap())
  }

  private data class Request(val roots: Collection<VirtualFile>) {
    override fun toString(): String = "Request(roots=${roots.joinToString(",") { it.name }})"
  }

  data class State(val gitState: Map<VirtualFile, List<GitFileStatus>>) {
    private val roots: Collection<VirtualFile>
      get() = gitState.keys

    fun updatedWith(newState: State, roots: Collection<VirtualFile> = this.roots.union(newState.roots)): State {
      val gitState = hashMapOf<VirtualFile, List<GitFileStatus>>()
      for (root in roots) {
        gitState[root] = newState.gitState[root] ?: this.gitState[root] ?: emptyList()
      }
      return State(gitState)
    }

    override fun toString(): String {
      return "State(gitState=$gitState)"
    }
  }

  private inner class MySingleTaskController : BaseSingleTaskController<Request, State>("Git Stage Tracker",
                                                                                        this::update, this) {
    override fun process(requests: List<Request>, previousState: State?): State {
      val gitStateRoots = requests.flatMapTo(HashSet()) { it.roots }
      val gitState = mutableMapOf<VirtualFile, List<GitFileStatus>>()
      for (root in gitStateRoots) {
        gitState[root] = getStatus(project, root, withIgnored = false).map { GitFileStatus(root, it) }
      }

      val newState = State(gitState)
      return previousState?.updatedWith(newState) ?: newState
    }
  }
}

interface GitStageTrackerListener : EventListener {
  fun update()
}