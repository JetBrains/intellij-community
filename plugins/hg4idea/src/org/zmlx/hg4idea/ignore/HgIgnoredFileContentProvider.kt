// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.ignore

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vcs.changes.IgnoreSettingsType
import com.intellij.openapi.vcs.changes.IgnoredFileContentProvider
import com.intellij.openapi.vcs.changes.IgnoredFileDescriptor
import com.intellij.openapi.vcs.changes.IgnoredFileProvider
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import org.zmlx.hg4idea.HgVcs
import org.zmlx.hg4idea.command.HgStatusCommand
import org.zmlx.hg4idea.repo.HgRepositoryFiles.HGIGNORE

private val LOG = logger<HgIgnoredFileContentProvider>()

class HgIgnoredFileContentProvider(private val project: Project) : IgnoredFileContentProvider {

  override fun getSupportedVcs(): VcsKey = HgVcs.getKey()

  override fun getFileName() = HGIGNORE

  override fun buildIgnoreFileContent(ignoreFileRoot: VirtualFile, ignoredFileProviders: Array<IgnoredFileProvider>): String {
    val hgRepoRoot = VcsUtil.getVcsRootFor(project, ignoreFileRoot)
    if (hgRepoRoot == null || hgRepoRoot != ignoreFileRoot) return ""  //generate .hgignore only in hg root

    val content = StringBuilder()
    val lineSeparator = System.lineSeparator()
    val untrackedFiles = getUntrackedFiles(hgRepoRoot, ignoreFileRoot)

    if (untrackedFiles.isEmpty()) return "" //if there is no untracked files this mean nothing to ignore

    for (provider in ignoredFileProviders) {
      val ignoredFiles = provider.getIgnoredFiles(project).ignoreBeansToRelativePaths(ignoreFileRoot, untrackedFiles)

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
    return if (content.isNotEmpty()) "syntax: glob$lineSeparator$lineSeparator$content" else ""
  }

  private fun getUntrackedFiles(hgRepoRoot: VirtualFile, ignoreFileRoot: VirtualFile): Set<VirtualFile> =
    try {
      HashSet(
        HgStatusCommand.Builder(false).unknown(true).removed(true).build(project).getFiles(hgRepoRoot)
          .filter { VfsUtil.isAncestor(ignoreFileRoot, it, true) }
      )
    }
    catch (e: VcsException) {
      LOG.warn("Cannot get untracked files: ", e)
      emptySet()
    }

  private fun Iterable<IgnoredFileDescriptor>.ignoreBeansToRelativePaths(ignoreFileRoot: VirtualFile,
                                                                         untrackedFiles: Set<VirtualFile>): List<String> {
    val vcsRoot = VcsUtil.getVcsRootFor(project, ignoreFileRoot)
    val vcsContextFactory = VcsContextFactory.SERVICE.getInstance()
    return filter { ignoredBean ->
      when (ignoredBean.type) {
        IgnoreSettingsType.UNDER_DIR -> shouldIgnoreUnderDir(ignoredBean, untrackedFiles, ignoreFileRoot, vcsRoot, vcsContextFactory)
        IgnoreSettingsType.FILE -> shouldIgnoreFile(ignoredBean, untrackedFiles, ignoreFileRoot, vcsRoot, vcsContextFactory)
        IgnoreSettingsType.MASK -> shouldIgnoreByMask(ignoredBean, untrackedFiles)
      }
    }.map { ignoredBean ->
      when (ignoredBean.type) {
        IgnoreSettingsType.MASK -> ignoredBean.mask!!
        IgnoreSettingsType.UNDER_DIR -> buildIgnoreEntryContent(ignoreFileRoot, ignoredBean)
        IgnoreSettingsType.FILE -> buildIgnoreEntryContent(ignoreFileRoot, ignoredBean)
      }
    }
  }

  private fun shouldIgnoreUnderDir(ignoredBean: IgnoredFileDescriptor,
                                   untrackedFiles: Set<VirtualFile>,
                                   ignoreFileRoot: VirtualFile,
                                   vcsRoot: VirtualFile?,
                                   vcsContextFactory: VcsContextFactory) =
    FileUtil.exists(ignoredBean.path)
    && untrackedFiles.any { FileUtil.isAncestor(ignoredBean.path!!, it.path, true) }
    && FileUtil.isAncestor(ignoreFileRoot.path, ignoredBean.path!!, false)
    && Comparing.equal(vcsRoot, VcsUtil.getVcsRootFor(project, vcsContextFactory.createFilePath(ignoredBean.path!!, true)))

  private fun shouldIgnoreFile(ignoredBean: IgnoredFileDescriptor,
                               untrackedFiles: Set<VirtualFile>,
                               ignoreFileRoot: VirtualFile,
                               vcsRoot: VirtualFile?,
                               vcsContextFactory: VcsContextFactory) =
    FileUtil.exists(ignoredBean.path)
    && untrackedFiles.any { ignoredBean.matchesFile(it) }
    && FileUtil.isAncestor(ignoreFileRoot.path, ignoredBean.path!!, false)
    && Comparing.equal(vcsRoot, VcsUtil.getVcsRootFor(project, vcsContextFactory.createFilePath(ignoredBean.path!!, false)))

  private fun shouldIgnoreByMask(ignoredBean: IgnoredFileDescriptor, untrackedFiles: Set<VirtualFile>) =
    untrackedFiles.any { ignoredBean.matchesFile(it) }

  override fun buildUnignoreContent(ignorePattern: String) = throw UnsupportedOperationException()

  override fun buildIgnoreGroupDescription(ignoredFileProvider: IgnoredFileProvider) =
    prependCommentHashCharacterIfNeeded(ignoredFileProvider.ignoredGroupDescription)

  override fun buildIgnoreEntryContent(ignoreFileRoot: VirtualFile, ignoredFileDescriptor: IgnoredFileDescriptor) =
    FileUtil.getRelativePath(ignoreFileRoot.path, ignoredFileDescriptor.path!!, '/') ?: ""

  override fun supportIgnoreFileNotInVcsRoot() = false

  private fun prependCommentHashCharacterIfNeeded(description: String): String =
    if (description.startsWith("#")) description else "# $description"
}