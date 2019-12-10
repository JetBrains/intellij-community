// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.IgnoredFileDescriptor
import com.intellij.openapi.vcs.changes.IgnoredFileProvider
import com.intellij.openapi.vcs.changes.ignore.IgnoredFileGeneratorImpl
import com.intellij.openapi.vcs.changes.ignore.IgnoredFileGeneratorImpl.needGenerateInternalIgnoreFile
import com.intellij.openapi.vcs.changes.ignore.psi.util.addNewElementsToIgnoreBlock
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import org.jetbrains.annotations.SystemIndependent
import java.util.concurrent.atomic.AtomicBoolean

private val LOG = logger<GitIgnoreInStoreDirGenerator>()

class GitIgnoreInStoreDirGeneratorActivity : StartupActivity.Background {
  override fun runActivity(project: Project) {
    if (!project.isDirectoryBased) return

    ProjectLevelVcsManagerImpl.getInstanceImpl(project).addInitializationRequest(VcsInitObject.AFTER_COMMON) {
      BackgroundTaskUtil.executeOnPooledThread(project, Runnable {
        with(project.service<GitIgnoreInStoreDirGenerator>()) {
          generateGitignoreInStoreDirIfNeeded(project)
        }
      })
    }
  }
}

/**
 * Generate .idea/.gitignore file silently after project create/open
 */
class GitIgnoreInStoreDirGenerator(private val project: Project) : SettingsSavingComponent {

  private var needGenerate = AtomicBoolean(true)

  override suspend fun save() {
    generateGitignoreInStoreDirIfNeeded(project)
  }

  internal fun generateGitignoreInStoreDirIfNeeded(project: Project) {
    if (!needGenerate.compareAndSet(true, false)) return

    val projectConfigDirPath = project.stateStore.projectConfigDir ?: return
    val projectConfigDirVFile = LocalFileSystem.getInstance().findFileByPath(projectConfigDirPath)
    if (projectConfigDirVFile == null) { // store dir (.idea) not created yet
      needGenerate.set(true)
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
      LOG.warn("Cannot check $projectFilePathStr for being unversioned", e)
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