// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.dvcs.DvcsUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.LocalFileSystem.WatchRequest
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.CommonProcessors
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import git4idea.index.vfs.GitIndexFileSystemRefresher.Companion.refreshRoots
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Listens to .git service files changes and updates [GitRepository] when needed.
 */
internal class GitRepositoryUpdater(
  private val repository: GitRepository,
  private val repositoryFiles: GitRepositoryFiles
) : Disposable, AsyncVfsEventsListener {
  private val rootDirs: Collection<VirtualFile> = repositoryFiles.rootDirs
  private val remotesDir: VirtualFile?
  private val headsDir: VirtualFile?
  private val tagsDir: VirtualFile?
  private val reftableDir: VirtualFile?
  private val watchRequests: Set<WatchRequest> = LocalFileSystem.getInstance().addRootsToWatch(rootDirs.map { it.path }, true)

  init {
    visitSubDirsInVfs()
    headsDir = VcsUtil.getVirtualFile(repositoryFiles.refsHeadsFile)
    remotesDir = VcsUtil.getVirtualFile(repositoryFiles.refsRemotesFile)
    tagsDir = VcsUtil.getVirtualFile(repositoryFiles.refsTagsFile)
    reftableDir = VcsUtil.getVirtualFile(repositoryFiles.reftableFile)
  }

  fun installListeners(parentDisposable: Disposable) {
    Disposer.register(parentDisposable, this)
    AsyncVfsEventsPostProcessor.getInstance().addListener(this, repository.coroutineScope)
  }

  override fun dispose() {
    LocalFileSystem.getInstance().removeWatchedRoots(watchRequests)
  }

  override suspend fun filesChanged(events: List<VFileEvent>) {
    val currentBranch = repository.currentBranch

    // which files in .git were changed
    var configChanged = false
    var indexChanged = false
    var headChanged = false
    var headMoved = false
    var branchFileChanged = false
    var currentBranchChanged = false
    var packedRefsChanged = false
    var rebaseFileChanged = false
    var mergeFileChanged = false
    var externallyCommitted = false
    var tagChanged = false
    var reftableChanged = false
    var gitignoreChanged = false

    val toReloadVfs = HashSet<VirtualFile>()
    for (event in events) {
      coroutineContext.ensureActive()

      val filePath = event.path
      if (isRootDirChange(event)) {
        when {
          repositoryFiles.isConfigFile(filePath) -> {
            configChanged = true
          }
          repositoryFiles.isIndexFile(filePath) -> {
            indexChanged = true
          }
          repositoryFiles.isHeadFile(filePath) -> {
            headChanged = true
          }
          repositoryFiles.isOrigHeadFile(filePath) -> {
            headMoved = true
          }
          repositoryFiles.isBranchFile(filePath) -> {
            // it is also possible, that a local branch with complex name ("folder/branch") was created => the folder also to be watched.
            branchFileChanged = true
            headsDir?.let { toReloadVfs.add(it) }

            if (currentBranch != null && repositoryFiles.isBranchFile(filePath, currentBranch.fullName)) {
              currentBranchChanged = true
            }
          }
          repositoryFiles.isRemoteBranchFile(filePath) -> {
            // it is possible that a branch from a new remote was fetch => we need to add a new remote folder to the VFS
            branchFileChanged = true
            ContainerUtil.addIfNotNull(toReloadVfs, remotesDir)
          }
          repositoryFiles.isPackedRefs(filePath) -> {
            packedRefsChanged = true
          }
          repositoryFiles.isRebaseFile(filePath) -> {
            rebaseFileChanged = true
          }
          repositoryFiles.isMergeFile(filePath) -> {
            mergeFileChanged = true
          }
          repositoryFiles.isCommitMessageFile(filePath) -> {
            externallyCommitted = true
          }
          repositoryFiles.isTagFile(filePath) -> {
            tagChanged = true
            ContainerUtil.addIfNotNull(toReloadVfs, tagsDir)
          }
          repositoryFiles.isReftableFile(filePath) -> {
            reftableChanged = true
            ContainerUtil.addIfNotNull(toReloadVfs, reftableDir)
          }
          repositoryFiles.isExclude(filePath) -> {
            // TODO watch file stored in `core.excludesfile`
            gitignoreChanged = true
          }
        }
      }
      else if (filePath.endsWith(GitRepositoryFiles.GITIGNORE)) {
        gitignoreChanged = true
      }
    }

    coroutineContext.ensureActive()

    for (dir in toReloadVfs) {
      VfsUtilCore.processFilesRecursively(dir, CommonProcessors.alwaysTrue())
    }

    if (headChanged || configChanged || branchFileChanged || packedRefsChanged || reftableChanged ||
        rebaseFileChanged || mergeFileChanged) {
      repository.update()
    }
    if (tagChanged || packedRefsChanged || reftableChanged) {
      repository.tagHolder.reload()
      BackgroundTaskUtil.syncPublisher(repository.project, GitRepository.GIT_REPO_CHANGE).repositoryChanged(repository)
    }
    if (configChanged) {
      BackgroundTaskUtil.syncPublisher(repository.project, GitConfigListener.TOPIC).notifyConfigChanged(repository)
    }
    if (indexChanged || externallyCommitted || headMoved || headChanged || currentBranchChanged || gitignoreChanged) {
      VcsDirtyScopeManager.getInstance(repository.project).dirDirtyRecursively(repository.root)
      repository.untrackedFilesHolder.invalidate()
    }
    if (indexChanged) {
      refreshRoots(repository.project, listOf(repository.root))
    }
  }

  private fun isRootDirChange(event: VFileEvent): Boolean {
    // can't do fast check, fallback to paths matching
    val file = event.file ?: return true
    return rootDirs.any { VfsUtilCore.isAncestor(it, file, false) }
  }

  private fun visitSubDirsInVfs() {
    for (rootDir in repositoryFiles.rootDirs) {
      rootDir.children
    }

    for (path in repositoryFiles.pathsToWatch) {
      DvcsUtil.ensureAllChildrenInVfs(LocalFileSystem.getInstance().refreshAndFindFileByPath(path))
    }
  }
}
