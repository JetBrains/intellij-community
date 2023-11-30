// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ignore

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.IgnoredFileDescriptor
import com.intellij.openapi.vcs.changes.IgnoredFileProvider
import com.intellij.openapi.vcs.changes.ignore.IgnoredFileGeneratorImpl
import com.intellij.openapi.vcs.changes.ignore.IgnoredFileGeneratorImpl.needGenerateInternalIgnoreFile
import com.intellij.openapi.vcs.changes.ignore.psi.util.addNewElementsToIgnoreBlock
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.systemIndependentPath
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import git4idea.GitNotificationIdsHolder
import git4idea.GitVcs
import git4idea.i18n.GitBundle
import git4idea.index.GitIndexUtil
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.SystemIndependent
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<GitIgnoreInStoreDirGenerator>()

private class GitIgnoreInStoreDirGeneratorActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!project.isDirectoryBased) {
      return
    }

    val completableDeferred = CompletableDeferred<Unit>()
    blockingContext {
      ProjectLevelVcsManager.getInstance(project).runAfterInitialization {
        completableDeferred.complete(Unit)
      }
    }
    completableDeferred.join()
    project.service<GitIgnoreInStoreDirGenerator>().run()
  }
}

/**
 * Generate .idea/.gitignore file silently after project create/open
 */
@Service(Service.Level.PROJECT)
internal class GitIgnoreInStoreDirGenerator(private val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
  private val needGenerate = AtomicBoolean(true)

  override fun dispose() {
  }

  suspend fun run() {
    val listenerRegistered = readAction { registerVfsListenerIfNeeded() }
    if (!listenerRegistered) {
      generateGitignoreInStoreDirIfNeeded()
    }
  }

  private fun registerVfsListenerIfNeeded(): Boolean {
    val projectConfigDirPath = project.stateStore.directoryStorePath
    if (projectConfigDirPath == null) {
      LOG.warn("Project config dir path not found. Project is default or not directory based.")
      needGenerate.set(false)
      return false
    }
    val projectConfigDirVFile = LocalFileSystem.getInstance().findFileByNioFile(projectConfigDirPath)
    if (projectConfigDirVFile != null && !needGenerateInternalIgnoreFile(project, projectConfigDirVFile)) {
      needGenerate.set(false)
      return false
    }
    val needRegister = projectConfigDirVFile == null
    if (needRegister) {
      LOG.debug(
        "Project config directory doesn't exist. Register VFS listener and try generate $GITIGNORE after config directory become available.")
      AsyncVfsEventsPostProcessor.getInstance().addListener(VfsEventsListener(project), this)
    }
    return needRegister
  }

  private inner class VfsEventsListener(private val project: Project) : AsyncVfsEventsListener {
    override fun filesChanged(events: List<VFileEvent>) {
      if (!needGenerate.get() || project.isDisposed) {
        return
      }

      if (affectedFilesInStoreDir(events)) {
        coroutineScope.launch {
          generateGitignoreInStoreDirIfNeeded()
        }
      }
    }

    private fun affectedFilesInStoreDir(events: List<VFileEvent>): Boolean {
      val projectConfigDirPath = project.stateStore.directoryStorePath?.systemIndependentPath ?: return false
      return events.asSequence()
        .mapNotNull(VFileEvent::getFile)
        .filter(VirtualFile::isInLocalFileSystem)
        .any { file -> file.exists() && inStoreDir(projectConfigDirPath, file.path) }
    }
  }

  @RequiresBackgroundThread
  fun generateGitignoreInStoreDirIfNeededSync() {
    runBlockingMaybeCancellable {
      generateGitignoreInStoreDirIfNeeded()
    }
  }

  suspend fun generateGitignoreInStoreDirIfNeeded() {
    if (!needGenerate.compareAndSet(true, false)) {
      return
    }

    val projectConfigDirPath = project.stateStore.directoryStorePath
    if (projectConfigDirPath == null) {
      LOG.warn("Project config dir path not found. Project is default or not directory based.")
      return
    }
    val projectConfigDirVFile = LocalFileSystem.getInstance().findFileByNioFile(projectConfigDirPath)
    if (projectConfigDirVFile == null) {
      LOG.warn("Project config dir not found in VFS by path $projectConfigDirPath")
      return
    }

    if (blockingContext { skipGeneration(project, projectConfigDirVFile, projectConfigDirPath) }) {
      return
    }

    try {
      doGenerate(project, projectConfigDirPath, projectConfigDirVFile)
    }
    catch (e: IOException) {
      LOG.warn(e)
      VcsNotifier.getInstance(project).notifyError(
        GitNotificationIdsHolder.IGNORE_FILE_GENERATION_ERROR,
        GitBundle.message("notification.ignore.file.generation.error.text.files.progress.title"),
        e.message.orEmpty())
    }
  }

  private fun skipGeneration(project: Project,
                             projectConfigDirVFile: VirtualFile,
                             projectConfigDirPath: Path): Boolean {
    return when {
      !needGenerateInternalIgnoreFile(project, projectConfigDirVFile) -> {
        needGenerate.set(false)
        true
      }
      VfsUtil.refreshAndFindChild(projectConfigDirVFile, GITIGNORE) != null -> {
        markGenerated(project, projectConfigDirVFile)
        true
      }
      haveNotGitVcs(project, projectConfigDirPath) -> {
        markGenerated(project, projectConfigDirVFile)
        true
      }
      isProjectSharedInGit(project) -> {
        markGenerated(project, projectConfigDirVFile)
        true
      }
      else -> false
    }
  }

  @Throws(IOException::class)
  private suspend fun doGenerate(project: Project, projectConfigDirPath: Path, projectConfigDirVFile: VirtualFile) {
    val gitVcsKey = GitVcs.getKey()
    val gitIgnoreContentProvider = VcsImplUtil.findIgnoredFileContentProvider(project, gitVcsKey) ?: return

    LOG.debug("Generate $GITIGNORE in $projectConfigDirPath for ${gitVcsKey.name}")

    val gitIgnoreFile = writeAction {
      projectConfigDirVFile.createChildData(projectConfigDirVFile, GITIGNORE)
    }

    for (ignoredFileProvider in IgnoredFileProvider.IGNORE_FILE.extensionList) {
      val ignoresInStoreDir = ignoredFileProvider.getIgnoredFiles(project).filter { ignore ->
        inStoreDir(projectConfigDirPath.systemIndependentPath, ignore)
      }
      if (ignoresInStoreDir.isEmpty()) {
        continue
      }

      val ignoredGroupDescription = gitIgnoreContentProvider.buildIgnoreGroupDescription(ignoredFileProvider)
      blockingContext {
        addNewElementsToIgnoreBlock(project, gitIgnoreFile, ignoredGroupDescription, gitVcsKey, *ignoresInStoreDir.toTypedArray())
      }
    }

    markGenerated(project, projectConfigDirVFile)
  }

  private fun haveNotGitVcs(project: Project, projectConfigDirPath: Path): Boolean {
    val projectConfigDir = VcsUtil.getFilePath(projectConfigDirPath.toFile(), true)
    val vcs = VcsUtil.getVcsFor(project, projectConfigDir) ?: return false
    return vcs.keyInstanceMethod != GitVcs.getKey()
  }

  private fun isProjectSharedInGit(project: Project): Boolean {
    val storeDir: @SystemIndependent String = project.stateStore.directoryStorePath?.systemIndependentPath ?: return false
    val storeDirPath = VcsUtil.getFilePath(storeDir, true)
    val vcsRoot = VcsUtil.getVcsRootFor(project, storeDirPath) ?: return false

    return try {
      GitIndexUtil.listStaged(project, vcsRoot, listOf(storeDirPath)).isNotEmpty()
    }
    catch (e: VcsException) {
      LOG.debug("Cannot check staged files in $storeDir", e)
      false
    }
  }

  private fun markGenerated(project: Project, projectConfigDirVFile: VirtualFile) {
    IgnoredFileGeneratorImpl.markIgnoreFileRootAsGenerated(project, projectConfigDirVFile.path)
    needGenerate.set(false)
  }

  private fun inStoreDir(projectConfigDirPath: @SystemIndependent String, ignore: IgnoredFileDescriptor): Boolean {
    val path = ignore.path ?: return false
    return inStoreDir(projectConfigDirPath, path)
  }

  private fun inStoreDir(projectConfigDirPath: @SystemIndependent String, path: @SystemIndependent String): Boolean {
    return FileUtil.isAncestor(projectConfigDirPath, path, true)
  }
}
