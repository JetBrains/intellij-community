// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Alarm
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import git4idea.commands.Git
import git4idea.repo.GitRepository
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

class GitRepositoryIgnoredHolder(private val project: Project, private val repository: GitRepository, private val git: Git) : Disposable {
  private val updateQueue = MergingUpdateQueue("GitIgnoreUpdate", 500, true, null, this, null, Alarm.ThreadToUse.POOLED_THREAD)
  private val inUpdateMode = AtomicBoolean(false)
  private val ignoredSet = hashSetOf<VirtualFile>()
  private val SET_LOCK = ReentrantReadWriteLock()
  private val listeners = EventDispatcher.create(GitRepositoryIgnoredHolderUpdateListener::class.java)

  @JvmOverloads
  fun startRescan(gitIgnorePath: String? = null) {
    if (scanTurnedOff()) return

    updateQueue.queue(object : Update("gitRescanIgnored") {
      override fun canEat(update: Update) = true

      override fun run() {
        if (inUpdateMode.compareAndSet(false, true)) {
          fireUpdateStarted(gitIgnorePath)
          rescanAllIgnored()
          inUpdateMode.set(false)
          fireUpdateFinished(gitIgnorePath)
        }
      }
    })
  }

  private fun scanTurnedOff() = !Registry.`is`("git.process.ignored")

  private fun fireUpdateStarted(gitIgnorePath: String? = null) {
    when(gitIgnorePath){
      null -> listeners.multicaster.updateStarted()
      else -> listeners.multicaster.updateStarted(gitIgnorePath)
    }
  }

  private fun fireUpdateFinished(gitIgnorePath: String? = null) {
    when(gitIgnorePath){
      null -> listeners.multicaster.updateFinished()
      else -> listeners.multicaster.updateFinished(gitIgnorePath)
    }
  }

  fun addUpdateStateListener(listener: GitRepositoryIgnoredHolderUpdateListener) {
    listeners.addListener(listener, this)
  }

  fun addFiles(files: List<VirtualFile>) = SET_LOCK.write { ignoredSet.addAll(files) }

  fun addFile(file: VirtualFile) = SET_LOCK.write { ignoredSet.add(file) }

  fun isInUpdateMode() = inUpdateMode.get()

  fun getIgnoredFiles(): Set<VirtualFile> = SET_LOCK.read { ignoredSet.toHashSet() }

  fun containsFile(file: VirtualFile) = SET_LOCK.read { ignoredSet.any { ignoredFile -> VfsUtil.isAncestor(ignoredFile, file, false) } }

  fun getSize() = SET_LOCK.read { ignoredSet.size }

  override fun dispose() {
    updateQueue.cancelAllUpdates()
    SET_LOCK.write {
      ignoredSet.clear()
    }
  }

  private fun rescanAllIgnored() {
    val ignored: Collection<VirtualFile> = HashSet(git.ignoredFiles(project, repository.root))
    SET_LOCK.write {
      ignoredSet.clear()
      ignoredSet.addAll(ignored)
    }
  }
}

interface GitRepositoryIgnoredHolderUpdateListener : EventListener {
  fun updateStarted()
  fun updateStarted(gitIgnorePath: String)

  fun updateFinished(gitIgnorePath: String)
  fun updateFinished()
}

abstract class GitRepositoryIgnoredHolderUpdateAdapter : GitRepositoryIgnoredHolderUpdateListener{
  override fun updateStarted() {}

  override fun updateStarted(gitIgnorePath: String) {}

  override fun updateFinished(gitIgnorePath: String) {}

  override fun updateFinished() {}
}