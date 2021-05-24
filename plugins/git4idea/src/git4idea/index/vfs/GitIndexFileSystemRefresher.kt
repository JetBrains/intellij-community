// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.vfs

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.PotemkinProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBusConnection
import com.intellij.vcsUtil.VcsUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener

class GitIndexFileSystemRefresher(private val project: Project) : Disposable {
  private val cache = Caffeine.newBuilder()
    .weakValues()
    .build<Key, GitIndexVirtualFile>(CacheLoader { key ->
      GitIndexVirtualFile(project, key.root, key.filePath)
    })

  init {
    val connection: MessageBusConnection = project.messageBus.connect(this)
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { repository ->
      LOG.debug("Scheduling refresh for repository ${repository.root.name}")
      refresh { it.root == repository.root }
    })
  }

  fun getFile(root: VirtualFile, filePath: FilePath): GitIndexVirtualFile {
    try {
      return cache.get(Key(root, filePath))!!
    }
    catch (e: Exception) {
      val cause = e.cause
      if (cause is ProcessCanceledException) {
        throw cause
      }
      throw e
    }
  }

  fun refresh(condition: (GitIndexVirtualFile) -> Boolean) {
    val filesToRefresh = cache.asMap().values.filter(condition)
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

  override fun dispose() {
    cache.invalidateAll()
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

  private data class Key(val root: VirtualFile, val filePath: FilePath)
}

fun writeInEdt(action: () -> Unit) {
  ApplicationManager.getApplication().invokeLater {
    ApplicationManager.getApplication().runWriteAction {
      action()
    }
  }
}