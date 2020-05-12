// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.vfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBusConnection
import com.intellij.vcs.log.submitSafe
import git4idea.repo.GitRepositoryManager
import git4idea.repo.GitUntrackedFilesHolder
import org.jetbrains.annotations.CalledWithWriteLock

class GitIndexFileSystemRefresher(private val project: Project) : Disposable {
  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Git Index Read/Write Thread for " + project.name, 1)

  init {
    val connection: MessageBusConnection = project.messageBus.connect(this)
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        val roots = GitRepositoryManager.getInstance(project).repositories.filter { repo ->
          events.any { e -> GitUntrackedFilesHolder.indexChanged(repo, e.path) }
        }.map { it.root }
        if (roots.isNotEmpty()) {
          LOG.debug("Scheduling refresh for ${roots.joinToString { it.name }}")
          refresh(roots)
        }
      }
    })
  }

  private fun refresh(roots: List<VirtualFile>) {
    executor.submitSafe(LOG) {
      val refreshRunnables = mutableListOf<GitIndexVirtualFile.Refresh>()
      project.serviceIfCreated<GitIndexVirtualFileCache>()?.forEachFile { file ->
        if (roots.contains(file.root)) {
          file.getRefresh()?.let { refreshRunnables.add(it) }
          LOG.debug("Preparing refresh for $file")
        }
      }
      if (refreshRunnables.isNotEmpty()) execute(refreshRunnables)
    }
  }

  internal fun execute(refreshList: List<GitIndexVirtualFile.Refresh>, postRunnable: Runnable? = null) {
    val events = refreshList.map { it.event }
    writeInEdt {
      ApplicationManager.getApplication().messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES).before(events)
      refreshList.forEach { it.run() }
      ApplicationManager.getApplication().messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES).after(events)
      postRunnable?.run()
    }
  }

  internal fun runTask(asynchronous: Boolean, task: () -> Unit) {
    if (ApplicationManager.getApplication().isDispatchThread || asynchronous) {
      executor.submitSafe(LOG, task)
    }
    else {
      task()
    }
  }

  @CalledWithWriteLock
  internal fun changeContent(file: GitIndexVirtualFile, requestor: Any?, modificationStamp: Long, writeCommand: () -> Unit) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val event = VFileContentChangeEvent(requestor, file, file.modificationStamp, modificationStamp, false)
    ApplicationManager.getApplication().messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES).before(listOf(event))
    executor.submitSafe(LOG) {
      try {
        writeCommand()
      }
      finally {
        writeInEdt { ApplicationManager.getApplication().messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES).after(listOf(event)) }
      }
    }
  }

  override fun dispose() {
  }

  companion object {
    private val LOG = Logger.getInstance(GitIndexFileSystemRefresher::class.java)
  }
}

fun writeInEdt(action: () -> Unit) {
  ApplicationManager.getApplication().invokeLater {
    ApplicationManager.getApplication().runWriteAction {
      action()
    }
  }
}