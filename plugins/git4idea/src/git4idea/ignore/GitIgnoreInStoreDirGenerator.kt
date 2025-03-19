// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ignore

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.IgnoreSettingsType
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
import kotlin.io.path.invariantSeparatorsPathString

private val LOG = logger<GitIgnoreInStoreDirGenerator>()

private class GitIgnoreInStoreDirGeneratorActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!project.isDirectoryBased) {
      return
    }

    val completableDeferred = CompletableDeferred<Unit>()
    project.serviceAsync<ProjectLevelVcsManager>().runAfterInitialization {
      completableDeferred.complete(Unit)
    }
    completableDeferred.join()
    project.service<GitIgnoreInStoreDirGenerator>().run()
  }
}

private class GitIgnoreInStoreDirSharedChecker : VcsSharedChecker {

  override fun getSupportedVcs(): VcsKey = GitVcs.getKey()

  override fun isPathSharedInVcs(project: Project, path: Path): Boolean {
    val projectConfigDirPath = project.stateStore.directoryStorePath ?: return false
    val projectConfigDir = VcsUtil.getFilePath(projectConfigDirPath.toFile(), true)
    val vcsRoot = VcsUtil.getVcsRootFor(project, projectConfigDir) ?: return false

    return GitIndexUtil.listStaged(project, vcsRoot, listOf(projectConfigDir)).isNotEmpty()
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
      AsyncVfsEventsPostProcessor.getInstance().addListener(VfsEventsListener(project), coroutineScope)
    }
    return needRegister
  }

  private inner class VfsEventsListener(private val project: Project) : AsyncVfsEventsListener {
    override suspend fun filesChanged(events: List<VFileEvent>) {
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
      val projectConfigDirPath = project.stateStore.directoryStorePath?.invariantSeparatorsPathString ?: return false
      return events.asSequence()
        .mapNotNull(VFileEvent::getFile)
        .filter(VirtualFile::isInLocalFileSystem)
        .any { file -> file.exists() && inStoreDir(projectConfigDirPath, file.path, true) }
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

  private fun skipGeneration(
    project: Project,
    projectConfigDirVFile: VirtualFile,
    projectConfigDirPath: Path,
  ): Boolean {
    return when {
      !needGenerateInternalIgnoreFile(project, projectConfigDirVFile) -> {
        needGenerate.set(false)
        true
      }
      VfsUtil.refreshAndFindChild(projectConfigDirVFile, GITIGNORE) != null -> {
        markGenerated(project, projectConfigDirVFile)
        true
      }
      isProjectSharedInVcs(project, projectConfigDirPath) -> {
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

    val gitIgnoreFile = edtWriteAction {
      projectConfigDirVFile.createChildData(projectConfigDirVFile, GITIGNORE)
    }

    for (ignoredFileProvider in IgnoredFileProvider.IGNORE_FILE.extensionList) {
      val ignoresInStoreDir = ignoredFileProvider.getIgnoredFiles(project).filter { ignore ->
        inStoreDir(projectConfigDirPath.invariantSeparatorsPathString, ignore)
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

  private fun isProjectSharedInVcs(project: Project, projectConfigDirPath: Path): Boolean {
    val projectConfigDir = VcsUtil.getFilePath(projectConfigDirPath.toFile(), true)
    val vcs = VcsUtil.getVcsFor(project, projectConfigDir) ?: return false
    try {
      val checker = VcsSharedChecker.EP_NAME.getExtensions(project).find { it.getSupportedVcs() == vcs.keyInstanceMethod }
      if (checker != null) {
        return checker.isPathSharedInVcs(project, projectConfigDirPath)
      }
    }
    catch (e: VcsException) {
      LOG.debug("Cannot check staged files in $projectConfigDirPath", e)
    }

    return false
  }

  private fun markGenerated(project: Project, projectConfigDirVFile: VirtualFile) {
    IgnoredFileGeneratorImpl.markIgnoreFileRootAsGenerated(project, projectConfigDirVFile.path)
    needGenerate.set(false)
  }

  private fun inStoreDir(projectConfigDirPath: @SystemIndependent String, ignore: IgnoredFileDescriptor): Boolean {
    val path = ignore.path ?: return false
    return inStoreDir(projectConfigDirPath, path, ignore.type != IgnoreSettingsType.MASK)
  }

  private fun inStoreDir(projectConfigDirPath: @SystemIndependent String, path: @SystemIndependent String, strict: Boolean): Boolean {
    return FileUtil.isAncestor(projectConfigDirPath, path, strict)
  }
}
