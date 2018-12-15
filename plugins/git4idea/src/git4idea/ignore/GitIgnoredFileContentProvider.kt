// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.NotIgnored
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
import git4idea.commands.Git
import git4idea.repo.GitRepositoryFiles.GITIGNORE
import java.io.File
import java.lang.System.lineSeparator

open class GitIgnoredFileContentProvider(private val project: Project) : IgnoredFileContentProvider {

  private val gitIgnoreChecker = GitIgnoreChecker(project)

  override fun getSupportedVcs(): VcsKey = GitVcs.getKey()

  override fun getFileName() = GITIGNORE

  override fun buildIgnoreFileContent(ignoreFileRoot: VirtualFile, ignoredFileProviders: Array<IgnoredFileProvider>): String {
    if (!GitUtil.isUnderGit(ignoreFileRoot)) return ""  //if ignore file root not under git --> return (e.g. in case if .git folder was deleted externally)

    val content = StringBuilder()
    val lineSeparator = lineSeparator()
    val untrackedFiles = Git.getInstance().untrackedFiles(project, ignoreFileRoot, null)

    for (i in ignoredFileProviders.indices) {
      val provider = ignoredFileProviders[i]
      val ignoredFiles = provider.getIgnoredFiles(project).ignoreBeansToRelativePaths(ignoreFileRoot, untrackedFiles)

      if (ignoredFiles.isEmpty()) continue

      if (!content.isEmpty()) {
        content.append(lineSeparator).append(lineSeparator)
      }

      val description = provider.ignoredGroupDescription
      if (description.isNotBlank()) {
        content.append(prependCommentHashCharacterIfNeeded(description))
        content.append(lineSeparator)
      }
      content.append(ignoredFiles.joinToString(lineSeparator))
    }
    return content.toString()
  }

  private fun Iterable<IgnoredFileDescriptor>.ignoreBeansToRelativePaths(ignoreFileRoot: VirtualFile, untrackedFiles: Set<VirtualFile>): List<String> {
    val vcsRoot= VcsUtil.getVcsRootFor(project, ignoreFileRoot)
    val vcsContextFactory = VcsContextFactory.SERVICE.getInstance()
    return filter { ignoredBean ->
      when (ignoredBean.type) {
        UNDER_DIR -> shouldIgnoreUnderDir(ignoredBean, ignoreFileRoot, vcsRoot, vcsContextFactory)
        FILE -> shouldIgnoreFile(ignoredBean, untrackedFiles, ignoreFileRoot, vcsRoot, vcsContextFactory)
        MASK -> shouldIgnoreByMask(ignoredBean, untrackedFiles)
      }
    }.map { ignoredBean ->
      when (ignoredBean.type) {
        MASK -> ignoredBean.mask!!
        UNDER_DIR -> "/${FileUtil.getRelativePath(ignoreFileRoot.path, ignoredBean.path!!, '/')!!}"
        FILE -> "/${FileUtil.getRelativePath(ignoreFileRoot.path, ignoredBean.path!!, '/')!!}"
      }
    }
  }

  private fun shouldIgnoreUnderDir(ignoredBean: IgnoredFileDescriptor,
                                   ignoreFileRoot: VirtualFile,
                                   vcsRoot: VirtualFile?,
                                   vcsContextFactory: VcsContextFactory) =
    FileUtil.exists(ignoredBean.path)
    && FileUtil.isAncestor(ignoreFileRoot.path, ignoredBean.path!!, false)
    && Comparing.equal(vcsRoot, VcsUtil.getVcsRootFor(project, vcsContextFactory.createFilePath(ignoredBean.path!!, true)))
    && gitIgnoreChecker.isIgnored(vcsRoot!!, File(ignoredBean.path!!)) is NotIgnored
    && shouldNotConsiderInternalIgnoreFile(ignoredBean, ignoreFileRoot)

  private fun shouldIgnoreFile(ignoredBean: IgnoredFileDescriptor,
                               untrackedFiles: Set<VirtualFile>,
                               ignoreFileRoot: VirtualFile,
                               vcsRoot: VirtualFile?,
                               vcsContextFactory: VcsContextFactory) =
    FileUtil.exists(ignoredBean.path)
    && untrackedFiles.any { ignoredBean.matchesFile(it) }
    && FileUtil.isAncestor(ignoreFileRoot.path, ignoredBean.path!!, false)
    && Comparing.equal(vcsRoot, VcsUtil.getVcsRootFor(project, vcsContextFactory.createFilePath(ignoredBean.path!!, false)))
    && shouldNotConsiderInternalIgnoreFile(ignoredBean, ignoreFileRoot)

  private fun shouldIgnoreByMask(ignoredBean: IgnoredFileDescriptor, untrackedFiles: Set<VirtualFile>) =
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

  private fun prependCommentHashCharacterIfNeeded(description: String): String =
    if (description.startsWith("#")) description else "# $description"
}
