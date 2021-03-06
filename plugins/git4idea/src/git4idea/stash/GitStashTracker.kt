// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import com.intellij.util.EventDispatcher
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.vcs.log.runInEdt
import git4idea.GitVcs
import git4idea.repo.GitRepositoryManager
import git4idea.ui.StashInfo
import java.util.*

class GitStashTracker(private val project: Project) : Disposable {
  private val eventDispatcher = EventDispatcher.create(GitStashTrackerListener::class.java)
  private val updateQueue = MergingUpdateQueue("GitStashTracker", 300, true, null, this, null, Alarm.ThreadToUse.POOLED_THREAD)

  var stashes = emptyMap<VirtualFile, Stashes>()
    private set

  init {
    val connection: MessageBusConnection = project.messageBus.connect(this)
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        if (GitRepositoryManager.getInstance(project).repositories.any { repo ->
            events.any { e -> repo.repositoryFiles.isStashReflogFile(e.path) }
          }) {
          scheduleRefresh()
        }
      }
    })
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsListener {
      scheduleRefresh()
    })
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      scheduleRefresh()
    }
  }

  fun scheduleRefresh() {
    if (!isStashToolWindowEnabled(project)) return

    updateQueue.queue(DisposableUpdate.createDisposable(this, "update", Runnable {
      val newStashes = mutableMapOf<VirtualFile, Stashes>()
      for (repo in GitRepositoryManager.getInstance(project).repositories) {
        try {
          newStashes[repo.root] = Stashes.Loaded(loadStashStack(project, repo.root))
        }
        catch (e: VcsException) {
          newStashes[repo.root] = Stashes.Error(e)
          LOG.warn(e)
        }
      }

      runInEdt(this) {
        stashes = newStashes

        eventDispatcher.multicaster.stashesUpdated()
      }
    }))
  }

  fun addListener(listener: GitStashTrackerListener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }

  fun getStashes(root: VirtualFile): List<StashInfo> {
    val stashes = stashes[root]
    return if (stashes is Stashes.Loaded) stashes.stashes else emptyList()
  }

  override fun dispose() {
    stashes = emptyMap()
  }

  companion object {
    private val LOG = Logger.getInstance(GitStashTracker::class.java)
  }

  sealed class Stashes {
    class Loaded(val stashes: List<StashInfo>) : Stashes()
    class Error(val error: VcsException) : Stashes()
  }
}

interface GitStashTrackerListener : EventListener {
  fun stashesUpdated()
}

fun GitStashTracker.isNotEmpty(): Boolean {
  return stashes.values.any { (it is GitStashTracker.Stashes.Error) || (it is GitStashTracker.Stashes.Loaded && it.stashes.isNotEmpty()) }
}

fun stashToolWindowRegistryOption() = Registry.get("git.enable.stash.toolwindow")
fun isStashToolWindowEnabled(project: Project): Boolean {
  return stashToolWindowRegistryOption().asBoolean() &&
         ProjectLevelVcsManager.getInstance(project).allActiveVcss.any { it.keyInstanceMethod == GitVcs.getKey() }
}

fun isStashToolWindowAvailable(project: Project): Boolean {
  return isStashToolWindowEnabled(project) &&
         project.serviceIfCreated<GitStashTracker>()?.isNotEmpty() == true
}