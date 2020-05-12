// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
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
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import org.jetbrains.annotations.SystemIndependent
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<GitIgnoreInStoreDirGenerator>()

class GitIgnoreInStoreDirGeneratorActivity : StartupActivity.Background {
  override fun runActivity(project: Project) {
    if (!project.isDirectoryBased || project.isDefault) return

    ProjectLevelVcsManager.getInstance(project).runAfterInitialization {
      project.service<GitIgnoreInStoreDirGenerator>().run()
    }
  }
}

/**
 * Generate .idea/.gitignore file silently after project create/open
 */
@Service
class GitIgnoreInStoreDirGenerator(private val project: Project) {

  private val needGenerate = AtomicBoolean(true)

  fun run() {
    val listenerRegistered = runReadAction { registerVfsListenerIfNeeded() }
    if (!listenerRegistered) {
      generateGitignoreInStoreDirIfNeeded()
    }
  }

  private fun registerVfsListenerIfNeeded(): Boolean {
    val projectConfigDirPath = project.stateStore.projectConfigDir
    if (projectConfigDirPath == null) {
      LOG.warn("Project config dir path not found. Project is default or not directory based.")
      needGenerate.set(false)
      return false
    }
    val projectConfigDirVFile = LocalFileSystem.getInstance().findFileByPath(projectConfigDirPath)
    if (projectConfigDirVFile != null && !needGenerateInternalIgnoreFile(project, projectConfigDirVFile)) {
      needGenerate.set(false)
      return false
    }
    val needRegister = projectConfigDirVFile == null || project.projectFile?.exists() != true
    if (needRegister) {
      LOG.debug(
        "Project file or project config directory doesn't exist. Register VFS listener and try generate $GITIGNORE after files become available.")
      AsyncVfsEventsPostProcessor.getInstance().addListener(VfsEventsListener(project), project)
    }
    return needRegister
  }

  private inner class VfsEventsListener(private val project: Project) : AsyncVfsEventsListener {
    override fun filesChanged(events: List<VFileEvent>) {
      if (!needGenerate.get() || project.isDisposed) return

      if (projectFileAffected(events)) {
        generateGitignoreInStoreDirIfNeeded()
      }
    }

    private fun projectFileAffected(events: List<VFileEvent>): Boolean =
      events.asSequence()
        .mapNotNull(VFileEvent::getFile)
        .filter(VirtualFile::isInLocalFileSystem)
        .any { file -> file == project.projectFile && file.exists() }

  }

  private fun generateGitignoreInStoreDirIfNeeded() {
    if (!needGenerate.compareAndSet(true, false)) return

    val projectConfigDirPath = project.stateStore.projectConfigDir
    if (projectConfigDirPath == null) {
      LOG.warn("Project config dir path not found. Project is default or not directory based.")
      return
    }
    val projectConfigDirVFile = LocalFileSystem.getInstance().findFileByPath(projectConfigDirPath)
    if (projectConfigDirVFile == null) {
      LOG.warn("Project config dir not found in VFS by path $projectConfigDirPath")
      return
    }

    if (skipGeneration(project, projectConfigDirVFile, projectConfigDirPath)) return

    doGenerate(project, projectConfigDirPath, projectConfigDirVFile)
  }

  private fun skipGeneration(project: Project,
                             projectConfigDirVFile: VirtualFile,
                             projectConfigDirPath: String): Boolean {
    if (!needGenerateInternalIgnoreFile(project, projectConfigDirVFile)) {
      needGenerate.set(false)
      return true
    }
    if (VfsUtil.refreshAndFindChild(projectConfigDirVFile, GITIGNORE) != null) {
      markGenerated(project, projectConfigDirVFile)
      return true
    }
    if (haveNotGitVcs(project, projectConfigDirPath)) {
      markGenerated(project, projectConfigDirVFile)
      return true
    }
    if (isProjectSharedInGit(project)) {
      markGenerated(project, projectConfigDirVFile)
      return true
    }

    return false
  }

  private fun doGenerate(project: Project,
                         projectConfigDirPath: String,
                         projectConfigDirVFile: VirtualFile) {
    val gitVcsKey = GitVcs.getKey()
    val gitIgnoreContentProvider = VcsImplUtil.findIgnoredFileContentProvider(project, gitVcsKey) ?: return

    LOG.debug("Generate $GITIGNORE in $projectConfigDirPath for ${gitVcsKey.name}")
    val gitIgnoreFile = createGitignore(projectConfigDirVFile)
    for (ignoredFileProvider in IgnoredFileProvider.IGNORE_FILE.extensions) {
      val ignoresInStoreDir =
        ignoredFileProvider.getIgnoredFiles(project).filter { ignore -> inStoreDir(projectConfigDirPath, ignore) }.toTypedArray()
      if (ignoresInStoreDir.isEmpty()) continue

      val ignoredGroupDescription = gitIgnoreContentProvider.buildIgnoreGroupDescription(ignoredFileProvider)
      addNewElementsToIgnoreBlock(project, gitIgnoreFile, ignoredGroupDescription, gitVcsKey, *ignoresInStoreDir)
    }

    markGenerated(project, projectConfigDirVFile)
  }

  private fun haveNotGitVcs(project: Project, projectConfigDirPath: String): Boolean {
    val projectConfigDir = VcsUtil.getFilePath(projectConfigDirPath)
    val vcs = VcsUtil.getVcsFor(project, projectConfigDir) ?: return false
    return vcs.keyInstanceMethod != GitVcs.getKey()
  }

  private fun isProjectSharedInGit(project: Project): Boolean {
    val projectFilePathStr: @SystemIndependent String = project.projectFilePath ?: return false
    val projectFilePath = VcsUtil.getFilePath(projectFilePathStr)
    val vcsRootForProjectFile = VcsUtil.getVcsRootFor(project, projectFilePath) ?: return false
    return try {
      Git.getInstance().untrackedFilePaths(project, vcsRootForProjectFile, listOf(projectFilePath)).isEmpty()
    }
    catch (e: VcsException) {
      LOG.debug("Cannot check $projectFilePathStr for being unversioned", e)
      false
    }
  }

  private fun markGenerated(project: Project, projectConfigDirVFile: VirtualFile) {
    IgnoredFileGeneratorImpl.markIgnoreFileRootAsGenerated(project, projectConfigDirVFile.path)
    needGenerate.set(false)
  }

  private fun createGitignore(inDir: VirtualFile) =
    invokeAndWaitIfNeeded { runWriteAction { inDir.createChildData(inDir, GITIGNORE) } }

  private fun inStoreDir(projectConfigDirPath: String, ignore: IgnoredFileDescriptor) =
    ignore.path?.let { FileUtil.isAncestor(projectConfigDirPath, it, true) } ?: false
}