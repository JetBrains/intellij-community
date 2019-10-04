// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.NotIgnored
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vcs.changes.IgnoreSettingsType.*
import com.intellij.openapi.vcs.changes.IgnoredFileContentProvider
import com.intellij.openapi.vcs.changes.IgnoredFileDescriptor
import com.intellij.openapi.vcs.changes.IgnoredFileProvider
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.stateStore
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import git4idea.repo.GitRepositoryManager
import java.io.File
import java.lang.System.lineSeparator

private val LOG = logger<GitIgnoredFileContentProvider>()

open class GitIgnoredFileContentProvider(private val project: Project) : IgnoredFileContentProvider {

  private val gitIgnoreChecker = GitIgnoreChecker(project)

  override fun getSupportedVcs(): VcsKey = GitVcs.getKey()

  override fun getFileName() = GITIGNORE

  override fun buildIgnoreFileContent(ignoreFileRoot: VirtualFile, ignoredFileProviders: Array<IgnoredFileProvider>): String {
    if (!GitUtil.isUnderGit(ignoreFileRoot)) return ""  //if ignore file root not under git --> return (e.g. in case if .git folder was deleted externally)
    val ignoreFileVcsRoot = VcsUtil.getVcsRootFor(project, ignoreFileRoot) ?: return ""

    val content = StringBuilder()
    val lineSeparator = lineSeparator()
    val untrackedFiles = getUntrackedFiles(ignoreFileVcsRoot)

    if (untrackedFiles.isEmpty()) return "" //if there is no untracked files this mean nothing to ignore

    for (provider in ignoredFileProviders) {
      val ignoredFiles = provider.getIgnoredFiles(project).ignoreBeansToRelativePaths(ignoreFileVcsRoot, ignoreFileRoot, untrackedFiles)
      if (ignoredFiles.isEmpty()) continue

      if (content.isNotEmpty()) {
        content.append(lineSeparator).append(lineSeparator)
      }

      val description = provider.ignoredGroupDescription
      if (description.isNotBlank()) {
        content.append(buildIgnoreGroupDescription(provider))
        content.append(lineSeparator)
      }
      content.append(ignoredFiles.joinToString(lineSeparator))
    }
    return content.toString()
  }

  private fun getUntrackedFiles(ignoreFileVcsRoot: VirtualFile): Set<FilePath> {
    try {
      val repo = GitRepositoryManager.getInstance(project).getRepositoryForRoot(ignoreFileVcsRoot) ?: return emptySet()
      return repo.untrackedFilesHolder.retrieveUntrackedFilePaths().toSet()
    }
    catch (e: VcsException) {
      LOG.warn("Cannot get untracked files: ", e)
      return emptySet()
    }
  }

  private fun Iterable<IgnoredFileDescriptor>.ignoreBeansToRelativePaths(ignoreFileVcsRoot: VirtualFile, ignoreFileRoot: VirtualFile, untrackedFiles: Set<FilePath>): List<String> {
    val vcsContextFactory = VcsContextFactory.SERVICE.getInstance()
    return filter { ignoredBean ->
      when (ignoredBean.type) {
        UNDER_DIR -> shouldIgnoreUnderDir(ignoredBean, untrackedFiles, ignoreFileRoot, ignoreFileVcsRoot, vcsContextFactory)
        FILE -> shouldIgnoreFile(ignoredBean, untrackedFiles, ignoreFileRoot, ignoreFileVcsRoot, vcsContextFactory)
        MASK -> shouldIgnoreByMask(ignoredBean, untrackedFiles)
      }
    }.map { ignoredBean ->
      when (ignoredBean.type) {
        MASK -> ignoredBean.mask!!
        UNDER_DIR -> buildIgnoreEntryContent(ignoreFileRoot, ignoredBean)
        FILE -> buildIgnoreEntryContent(ignoreFileRoot, ignoredBean)
      }
    }
  }

  private fun shouldIgnoreUnderDir(ignoredBean: IgnoredFileDescriptor,
                                   untrackedFiles: Set<FilePath>,
                                   ignoreFileRoot: VirtualFile,
                                   ignoreFileVcsRoot: VirtualFile,
                                   vcsContextFactory: VcsContextFactory) =
    FileUtil.exists(ignoredBean.path)
    && untrackedFiles.any { FileUtil.isAncestor(ignoredBean.path!!, it.path, true) }
    && FileUtil.isAncestor(ignoreFileRoot.path, ignoredBean.path!!, false)
    && Comparing.equal(ignoreFileVcsRoot, VcsUtil.getVcsRootFor(project, vcsContextFactory.createFilePath(ignoredBean.path!!, true)))
    && gitIgnoreChecker.isIgnored(ignoreFileVcsRoot, File(ignoredBean.path!!)) is NotIgnored
    && shouldNotConsiderInternalIgnoreFile(ignoredBean, ignoreFileRoot)

  private fun shouldIgnoreFile(ignoredBean: IgnoredFileDescriptor,
                               untrackedFiles: Set<FilePath>,
                               ignoreFileRoot: VirtualFile,
                               ignoreFileVcsRoot: VirtualFile,
                               vcsContextFactory: VcsContextFactory) =
    FileUtil.exists(ignoredBean.path)
    && untrackedFiles.any { ignoredBean.matchesFile(it) }
    && FileUtil.isAncestor(ignoreFileRoot.path, ignoredBean.path!!, false)
    && Comparing.equal(ignoreFileVcsRoot, VcsUtil.getVcsRootFor(project, vcsContextFactory.createFilePath(ignoredBean.path!!, false)))
    && shouldNotConsiderInternalIgnoreFile(ignoredBean, ignoreFileRoot)

  private fun shouldIgnoreByMask(ignoredBean: IgnoredFileDescriptor, untrackedFiles: Set<FilePath>) =
    untrackedFiles.any { ignoredBean.matchesFile(it) }

  private fun shouldNotConsiderInternalIgnoreFile(ignoredBean: IgnoredFileDescriptor, ignoreFileRoot: VirtualFile): Boolean {
    val insideDirectoryStore = ignoredBean.path?.contains(Project.DIRECTORY_STORE_FOLDER) ?: false
    if (insideDirectoryStore) {
      val directoryStoreOrProjectFileLocation = project.stateStore.directoryStoreFile ?: project.projectFile?.parent ?: return false
      return FileUtil.isAncestor(VfsUtilCore.virtualToIoFile(directoryStoreOrProjectFileLocation),
                                 VfsUtilCore.virtualToIoFile(ignoreFileRoot), false)
    }
    return true
  }

  override fun buildUnignoreContent(ignorePattern: String) = StringBuilder().apply {
    append(lineSeparator())
    append("!$ignorePattern")
  }.toString()

  override fun buildIgnoreGroupDescription(ignoredFileProvider: IgnoredFileProvider) =
    prependCommentHashCharacterIfNeeded(ignoredFileProvider.ignoredGroupDescription)

  override fun buildIgnoreEntryContent(ignoreEntryRoot: VirtualFile, ignoredFileDescriptor: IgnoredFileDescriptor) =
    "/${FileUtil.getRelativePath(ignoreEntryRoot.path, ignoredFileDescriptor.path!!, '/') ?: ""}"

  private fun prependCommentHashCharacterIfNeeded(description: String): String =
    if (description.startsWith("#")) description else "# $description"
}
