// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.vfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.PotemkinProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.messages.MessageBusConnection
import com.intellij.vcsUtil.VcsUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import git4idea.repo.GitUntrackedFilesHolder

class GitIndexFileSystemRefresher(private val project: Project) : Disposable {
  private val cache = GitIndexVirtualFileCache(project)

  init {
    val connection: MessageBusConnection = project.messageBus.connect(this)
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        val roots = GitRepositoryManager.getInstance(project).repositories.filter { repo ->
          events.any { e -> GitUntrackedFilesHolder.indexChanged(repo, e.path) }
        }.map { it.root }
        if (roots.isNotEmpty()) {
          LOG.debug("Scheduling refresh for roots ${roots.joinToString { it.name }}")
          refresh { roots.contains(it.root) }
        }
      }
    })
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { repository ->
      LOG.debug("Scheduling refresh for repository ${repository.root.name}")
      refresh { it.root == repository.root }
    })
  }

  fun getFile(root: VirtualFile, filePath: FilePath): GitIndexVirtualFile {
    return cache.get(root, filePath)
  }

  fun refresh(condition: (GitIndexVirtualFile) -> Boolean) {
    val filesToRefresh = cache.filesMatching(condition)
    if (filesToRefresh.isEmpty()) return
    refresh(filesToRefresh)
  }

  internal fun refresh(filesToRefresh: List<GitIndexVirtualFile>, async: Boolean = true, postRunnable: Runnable? = null) {
    LOG.debug("Creating ${if (async) "async" else "sync"} refresh session for ${filesToRefresh.joinToString { it.path }}")
    val session = RefreshSession(filesToRefresh, postRunnable)
    if (async || !ApplicationManager.getApplication().isDispatchThread) {
      val refresh = AppExecutorUtil.getAppExecutorService().submit {
        session.read()
        writeInEdt {
          session.apply()
        }
      }
      if (!async) refresh.get()
    }
    else if (ApplicationManager.getApplication().isWriteAccessAllowed) {
      PotemkinProgress(GitBundle.message("stage.vfs.refresh.process"), project, null, null).runInBackground {
        session.read()
      }
      session.apply()
    }
    else {
      ProgressManager.getInstance().runProcessWithProgressSynchronously({ session.read() }, GitBundle.message("stage.vfs.refresh.process"),
                                                                        false, project)
      ApplicationManager.getApplication().runWriteAction { session.apply() }
    }
  }

  @RequiresWriteLock
  internal fun changeContent(file: GitIndexVirtualFile, requestor: Any?, modificationStamp: Long, writeCommand: () -> Unit) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val event = VFileContentChangeEvent(requestor, file, file.modificationStamp, modificationStamp, false)
    ApplicationManager.getApplication().messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES).before(listOf(event))
    PotemkinProgress(GitBundle.message("stage.vfs.write.process", file.name), project, null, null).runInBackground {
      writeCommand()
    }
    ApplicationManager.getApplication().messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES).after(listOf(event))
  }

  override fun dispose() {
    Disposer.dispose(cache)
  }

  companion object {
    private val LOG = Logger.getInstance(GitIndexFileSystemRefresher::class.java)

    @JvmStatic
    fun getInstance(project: Project) = project.service<GitIndexFileSystemRefresher>()

    @JvmStatic
    fun refreshFilePaths(project: Project, paths: Collection<FilePath>) {
      val pathsSet = paths.toSet()
      project.serviceIfCreated<GitIndexFileSystemRefresher>()?.refresh { pathsSet.contains(it.filePath) }
    }

    @JvmStatic
    fun refreshFilePaths(project: Project, paths: Map<VirtualFile, Collection<FilePath>>) {
      project.serviceIfCreated<GitIndexFileSystemRefresher>()?.refresh {
        paths[it.root]?.contains(it.filePath) == true
      }
    }

    @JvmStatic
    fun refreshVirtualFiles(project: Project, paths: Collection<VirtualFile>) {
      refreshFilePaths(project, paths.map(VcsUtil::getFilePath))
    }

    @JvmStatic
    fun refreshRoots(project: Project, roots: Collection<VirtualFile>) {
      project.serviceIfCreated<GitIndexFileSystemRefresher>()?.refresh {
        roots.contains(it.root)
      }
    }
  }

  private class RefreshSession(private val filesToRefresh: List<GitIndexVirtualFile>, private val postRunnable: Runnable?) {
    private val refreshList = mutableListOf<GitIndexVirtualFile.Refresh>()

    fun read() {
      LOG.assertTrue(!ApplicationManager.getApplication().isDispatchThread)
      for (file in filesToRefresh) {
        file.getRefresh()?.let { refreshList.add(it) }
      }
    }

    fun apply() {
      ApplicationManager.getApplication().assertWriteAccessAllowed()
      if (refreshList.isEmpty()) {
        postRunnable?.run()
        return
      }
      val events = refreshList.map { it.event }
      ApplicationManager.getApplication().messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES).before(events)
      refreshList.forEach { it.run() }
      ApplicationManager.getApplication().messageBus.syncPublisher(VirtualFileManager.VFS_CHANGES).after(events)
      postRunnable?.run()
    }
  }
}

fun writeInEdt(action: () -> Unit) {
  ApplicationManager.getApplication().invokeLater {
    ApplicationManager.getApplication().runWriteAction {
      action()
    }
  }
}