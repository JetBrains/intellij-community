// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsListener
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
        if (roots.isNotEmpty()) {
          singleTaskController.request(Request(roots))
        }
      }
    })
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsListener {
      val roots = gitRoots()
      if (roots.isNotEmpty()) {
        singleTaskController.request(Request(roots))
      }
      else {
        runInEdt(this) {
          update(State(emptyMap()))
        }
      }
    })
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