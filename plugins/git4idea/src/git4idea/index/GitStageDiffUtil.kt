// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffContentFactoryEx
import com.intellij.diff.DiffRequestFactoryImpl
import com.intellij.diff.chains.DiffRequestProducerException
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeys
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.actions.diff.UnversionedDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.impl.ContentRevisionCache
import com.intellij.openapi.vcs.merge.MergeUtils.putRevisionInfos
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.GitContentRevision
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.i18n.GitBundle
import git4idea.index.KindTag.Companion.getTag
import git4idea.index.ui.GitFileStatusNode
import git4idea.index.ui.NodeKind
import git4idea.index.vfs.GitIndexFileSystemRefresher
import git4idea.index.vfs.GitIndexVirtualFile
import git4idea.merge.GitMergeUtil
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitFileUtils
import org.jetbrains.annotations.Nls
import java.io.IOException

fun createTwoSidesDiffRequestProducer(project: Project, statusNode: GitFileStatusNode): ChangeDiffRequestChain.Producer? {
  return when (statusNode.kind) {
    NodeKind.STAGED -> StagedProducer(project, statusNode)
    NodeKind.UNSTAGED -> UnStagedProducer(project, statusNode)
    NodeKind.CONFLICTED -> MergedProducer(project, statusNode)
    NodeKind.UNTRACKED -> UnversionedDiffRequestProducer.create(project, statusNode.filePath, getTag(NodeKind.UNSTAGED))
    NodeKind.IGNORED -> null
  }
}

fun createThreeSidesDiffRequestProducer(project: Project, statusNode: GitFileStatusNode): ChangeDiffRequestChain.Producer? {
  val hasThreeSides = statusNode.has(ContentVersion.HEAD) && statusNode.has(ContentVersion.STAGED) && statusNode.has(ContentVersion.LOCAL)
  return when (statusNode.kind) {
    NodeKind.STAGED -> if (hasThreeSides) ThreeSidesProducer(project, statusNode) else StagedProducer(project, statusNode)
    NodeKind.UNSTAGED -> if (hasThreeSides) ThreeSidesProducer(project, statusNode) else UnStagedProducer(project, statusNode)
    NodeKind.CONFLICTED -> MergedProducer(project, statusNode)
    NodeKind.UNTRACKED -> UnversionedDiffRequestProducer.create(project, statusNode.filePath, getTag(NodeKind.UNSTAGED))
    NodeKind.IGNORED -> null
  }
}

fun createChange(project: Project, root: VirtualFile, status: GitFileStatus,
                 beforeVersion: ContentVersion, afterVersion: ContentVersion): Change? {
  val bRev = createContentRevision(project, root, status, beforeVersion)
  val aRev = createContentRevision(project, root, status, afterVersion)
  return if (bRev != null || aRev != null) Change(bRev, aRev) else null
}

private fun createContentRevision(project: Project, root: VirtualFile, status: GitFileStatus, version: ContentVersion): ContentRevision? {
  if (!status.has(version)) return null
  return when (version) {
    ContentVersion.HEAD -> HeadContentRevision(project, root, status)
    ContentVersion.STAGED -> StagedContentRevision(project, root, status)
    ContentVersion.LOCAL -> CurrentContentRevision(status.path(version))
  }
}

@Throws(VcsException::class, IOException::class)
private fun headDiffContent(project: Project, root: VirtualFile, status: GitFileStatus): DiffContent {
  if (!status.has(ContentVersion.HEAD)) return DiffContentFactory.getInstance().createEmpty()

  val submodule = GitContentRevision.getRepositoryIfSubmodule(project, status.path)
  if (submodule != null) {
    val hash = GitIndexUtil.loadSubmoduleHashAt(submodule.repository, submodule.parent, GitRevisionNumber.HEAD)
               ?: throw VcsException(DiffBundle.message("error.cant.show.diff.cant.load.revision.content"))
    return DiffContentFactory.getInstance().create(project, hash.asString())
  }

  val headContent = headContentBytes(project, root, status)
  return DiffContentFactoryEx.getInstanceEx().createFromBytes(project, headContent, status.path)
}

@Throws(VcsException::class)
private fun stagedDiffContent(project: Project, root: VirtualFile, status: GitFileStatus): DiffContent {
  if (!status.has(ContentVersion.STAGED)) return DiffContentFactory.getInstance().createEmpty()

  val submodule = GitContentRevision.getRepositoryIfSubmodule(project, status.path)
  if (submodule != null) {
    val hash = GitIndexUtil.loadStagedSubmoduleHash(submodule.repository, submodule.parent)
    return DiffContentFactory.getInstance().create(project, hash.asString())
  }

  val indexFile = stagedContentFile(project, root, status)
  return DiffContentFactory.getInstance().create(project, indexFile)
}

@Throws(VcsException::class)
private fun localDiffContent(project: Project, status: GitFileStatus): DiffContent {
  if (!status.has(ContentVersion.LOCAL)) return DiffContentFactory.getInstance().createEmpty()

  val submodule = GitContentRevision.getRepositoryIfSubmodule(project, status.path)
  if (submodule != null) {
    val revision = submodule.repository.currentRevision
                   ?: throw VcsException(DiffBundle.message("error.cant.show.diff.cant.load.revision.content"))
    return DiffContentFactory.getInstance().create(project, revision)
  }

  val localFile: VirtualFile = status.path(ContentVersion.LOCAL).virtualFile
                               ?: throw VcsException(GitBundle.message("stage.diff.local.content.exception.message", status.path))
  return DiffContentFactory.getInstance().create(project, localFile)
}

@Throws(VcsException::class)
private fun headContentBytes(project: Project, root: VirtualFile, status: GitFileStatus): ByteArray {
  val filePath = status.path(ContentVersion.HEAD)
  return GitFileUtils.getFileContent(project, root, GitUtil.HEAD, VcsFileUtil.relativePath(root, filePath))
}

@Throws(VcsException::class)
private fun stagedContentFile(project: Project, root: VirtualFile, statusNode: GitFileStatus): VirtualFile {
  val filePath = statusNode.path(ContentVersion.STAGED)
  return GitIndexFileSystemRefresher.getInstance(project).getFile(root, filePath)
}

fun compareHeadWithStaged(project: Project, root: VirtualFile, status: GitFileStatus): DiffRequest {
  return StagedDiffRequest(headDiffContent(project, root, status),
                           stagedDiffContent(project, root, status),
                           GitUtil.HEAD, GitBundle.message("stage.content.staged"),
                           getTitle(status, NodeKind.STAGED)).apply {
    putUserData(DiffUserDataKeysEx.VCS_DIFF_ACCEPT_LEFT_ACTION_TEXT, GitBundle.message("action.label.reset.staged.range"))
  }
}

fun compareStagedWithLocal(project: Project, root: VirtualFile, status: GitFileStatus): DiffRequest {
  return StagedDiffRequest(stagedDiffContent(project, root, status),
                           localDiffContent(project, status),
                           GitBundle.message("stage.content.staged"), GitBundle.message("stage.content.local"),
                           getTitle(status, NodeKind.UNSTAGED)).apply {
    putUserData(DiffUserDataKeysEx.VCS_DIFF_ACCEPT_RIGHT_ACTION_TEXT, GitBundle.message("action.label.add.unstaged.range"))
    putUserData(DiffUserDataKeysEx.VCS_DIFF_ACCEPT_LEFT_ACTION_TEXT, DiffBundle.message("action.presentation.diff.revert.text"))
  }
}

fun compareThreeVersions(project: Project, root: VirtualFile, status: GitFileStatus): DiffRequest {
  val title = getTitle(status)
  return StagedDiffRequest(headDiffContent(project, root, status),
                           stagedDiffContent(project, root, status),
                           localDiffContent(project, status),
                           GitUtil.HEAD, GitBundle.message("stage.content.staged"), GitBundle.message("stage.content.local"),
                           title).apply {
    putUserData(DiffUserDataKeys.THREESIDE_DIFF_COLORS_MODE,
                DiffUserDataKeys.ThreeSideDiffColors.LEFT_TO_RIGHT)
    putUserData(DiffUserDataKeysEx.VCS_DIFF_ACCEPT_RIGHT_TO_BASE_ACTION_TEXT, GitBundle.message("action.label.add.unstaged.range"))
    putUserData(DiffUserDataKeysEx.VCS_DIFF_ACCEPT_BASE_TO_RIGHT_ACTION_TEXT, DiffBundle.message("action.presentation.diff.revert.text"))
    putUserData(DiffUserDataKeysEx.VCS_DIFF_ACCEPT_LEFT_TO_BASE_ACTION_TEXT, GitBundle.message("action.label.reset.staged.range"))
  }
}

private class UnStagedProducer constructor(private val project: Project, file: GitFileStatusNode) : GitFileStatusNodeProducerBase(file) {
  @Throws(VcsException::class)
  override fun processImpl(): DiffRequest {
    return compareStagedWithLocal(project, statusNode.root, statusNode.status)
  }
}

private class StagedProducer constructor(private val project: Project, file: GitFileStatusNode) : GitFileStatusNodeProducerBase(file) {
  @Throws(VcsException::class, IOException::class)
  override fun processImpl(): DiffRequest {
    return compareHeadWithStaged(project, statusNode.root, statusNode.status)
  }
}

class ThreeSidesProducer(private val project: Project,
                         statusNode: GitFileStatusNode) : GitFileStatusNodeProducerBase(statusNode) {
  @Throws(VcsException::class, IOException::class)
  override fun processImpl(): DiffRequest {
    return compareThreeVersions(project, statusNode.root, statusNode.status)
  }
}

class MergedProducer(private val project: Project,
                     statusNode: GitFileStatusNode) : GitFileStatusNodeProducerBase(statusNode) {

  @Throws(VcsException::class, IOException::class)
  override fun processImpl(): DiffRequest {
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(statusNode.root)
    val mergeData = GitMergeUtil.loadMergeData(project, statusNode.root, statusNode.filePath,
                                               repository?.let { GitMergeUtil.isReverseRoot(it) } ?: false)

    val title = getTitle(statusNode.status, statusNode.kind)
    val titles = listOf(ChangeDiffRequestProducer.getYourVersion(),
                        ChangeDiffRequestProducer.getBaseVersion(),
                        ChangeDiffRequestProducer.getServerVersion())
    val contents = listOf(mergeData.CURRENT, mergeData.ORIGINAL, mergeData.LAST).map {
      DiffContentFactory.getInstance().createFromBytes(project, it, statusNode.filePath.fileType, statusNode.filePath.name)
    }
    val request = SimpleDiffRequest(title, contents, titles)
    putRevisionInfos(request, mergeData)

    return request
  }
}

private class StagedDiffRequest(contents: List<DiffContent>, titles: List<String>, @Nls title: String? = null) :
  SimpleDiffRequest(title, contents, titles) {

  constructor(content1: DiffContent, content2: DiffContent, @Nls title1: String, @Nls title2: String, @Nls title: String? = null) :
    this(listOf(content1, content2), listOf(title1, title2), title)

  constructor(content1: DiffContent, content2: DiffContent, content3: DiffContent,
              @Nls title1: String, @Nls title2: String, @Nls title3: String, @Nls title: String? = null) :
    this(listOf(content1, content2, content3), listOf(title1, title2, title3), title)

  override fun onAssigned(isAssigned: Boolean) {
    super.onAssigned(isAssigned)
    if (!isAssigned) {
      for (content in contents) {
        if (content is DocumentContent) {
          val file = FileDocumentManager.getInstance().getFile(content.document)
          if (file is GitIndexVirtualFile) {
            FileDocumentManager.getInstance().saveDocument(content.document)
          }
        }
      }
    }
  }
}

abstract class GitFileStatusNodeProducerBase(val statusNode: GitFileStatusNode) : ChangeDiffRequestChain.Producer {
  private val kind = statusNode.kind

  @Throws(VcsException::class, IOException::class)
  abstract fun processImpl(): DiffRequest

  @Throws(DiffRequestProducerException::class)
  override fun process(context: UserDataHolder, indicator: ProgressIndicator): DiffRequest {
    try {
      return processImpl()
    }
    catch (e: VcsException) {
      throw DiffRequestProducerException(e)
    }
    catch (e: IOException) {
      throw DiffRequestProducerException(e)
    }
  }

  override fun getFilePath(): FilePath {
    return statusNode.filePath
  }

  override fun getFileStatus(): FileStatus {
    return statusNode.fileStatus
  }

  override fun getName(): String {
    return statusNode.filePath.presentableUrl
  }

  override fun getPopupTag(): ChangesBrowserNode.Tag? {
    return getTag(kind)
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false

    val wrapper = o as GitFileStatusNodeProducerBase
    return statusNode == wrapper.statusNode
  }

  override fun hashCode(): Int {
    return statusNode.hashCode()
  }
}

private class HeadContentRevision(val project: Project, val root: VirtualFile, val status: GitFileStatus) : ByteBackedContentRevision {
  override fun getFile(): FilePath = status.path(ContentVersion.HEAD)
  override fun getRevisionNumber(): VcsRevisionNumber = TextRevisionNumber(GitUtil.HEAD)

  override fun getContent(): String? = ContentRevisionCache.getAsString(contentAsBytes, file, null)

  @Throws(VcsException::class)
  override fun getContentAsBytes(): ByteArray = headContentBytes(project, root, status)
}

private class StagedContentRevision(val project: Project, val root: VirtualFile, val status: GitFileStatus) : ByteBackedContentRevision {
  override fun getFile(): FilePath = status.path(ContentVersion.STAGED)
  override fun getRevisionNumber(): VcsRevisionNumber = TextRevisionNumber(GitBundle.message("stage.content.staged"))

  override fun getContent(): String? = ContentRevisionCache.getAsString(contentAsBytes, file, null)

  @Throws(VcsException::class)
  override fun getContentAsBytes(): ByteArray = stagedContentFile(project, root, status).contentsToByteArray()
}

private class KindTag(private val kind: NodeKind) : ChangesBrowserNode.Tag {
  override fun toString(): String = GitBundle.message(kind.key)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as KindTag

    if (kind != other.kind) return false

    return true
  }

  override fun hashCode(): Int {
    return kind.hashCode()
  }

  companion object {
    private val tags = NodeKind.values().associateWith { KindTag(it) }
    internal fun getTag(nodeKind: NodeKind) = tags[nodeKind]!!
  }
}

private fun GitFileStatusNode.has(contentVersion: ContentVersion): Boolean = status.has(contentVersion)

@Nls
private fun getTitle(status: GitFileStatus, kind: NodeKind): String {
  return DiffRequestFactoryImpl.getTitle(status.path, kind.origPath(status),
                                         DiffRequestFactoryImpl.DIFF_TITLE_RENAME_SEPARATOR)
}

@Nls
private fun getTitle(status: GitFileStatus): String {
  return DiffRequestFactoryImpl.getTitle(status.path, status.origPath, DiffRequestFactoryImpl.DIFF_TITLE_RENAME_SEPARATOR)
}