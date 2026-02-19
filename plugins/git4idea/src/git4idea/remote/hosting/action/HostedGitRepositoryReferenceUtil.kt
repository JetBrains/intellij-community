// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting.action

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.CommitId
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitFileRevision
import git4idea.GitNotificationIdsHolder
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.history.GitHistoryUtils
import git4idea.i18n.GitBundle
import git4idea.remote.hosting.HostedGitRepositoriesManager
import git4idea.remote.hosting.findKnownRepositories
import git4idea.repo.GitRepository
import org.jetbrains.annotations.Nls
import java.net.URI

object HostedGitRepositoryReferenceUtil {
  private fun findReferences(
    repositoryManager: HostedGitRepositoriesManager<*>, repository: GitRepository, revision: GitRevisionNumber,
    uriProducer: (repository: URI, revisionHash: String) -> URI
  ): List<HostedGitRepositoryReference> {
    val accessibleRepositories = repositoryManager.findKnownRepositories(repository)
    if (accessibleRepositories.isEmpty()) return emptyList()

    return accessibleRepositories.map {
      uriProducer(it.repository.getWebURI(), revision.asString()).let(HostedGitRepositoryReference::WebURI)
    }
  }

  fun findReferences(
    project: Project, repositoryManager: HostedGitRepositoriesManager<*>, file: VirtualFile, revision: GitRevisionNumber,
    uriProducer: (repository: URI, revisionHash: String) -> URI
  ): List<HostedGitRepositoryReference> {
    val filePath = VcsUtil.getFilePath(file)
    val repository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(filePath) ?: return emptyList()
    return findReferences(repositoryManager, repository, revision, uriProducer)
  }

  fun findReferences(
    project: Project, repositoryManager: HostedGitRepositoriesManager<*>, revision: GitFileRevision,
    uriProducer: (repository: URI, revisionHash: String) -> URI
  ): List<HostedGitRepositoryReference> {
    val repository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(revision.path) ?: return emptyList()
    val revisionNumber = revision.revisionNumber as? GitRevisionNumber ?: return emptyList()
    return findReferences(repositoryManager, repository, revisionNumber, uriProducer)
  }

  fun findReferences(
    project: Project, repositoryManager: HostedGitRepositoriesManager<*>, commit: CommitId,
    uriProducer: (repository: URI, revisionHash: String) -> URI
  ): List<HostedGitRepositoryReference> {
    val repository = GitUtil.getRepositoryManager(project).getRepositoryForRootQuick(commit.root) ?: return emptyList()

    val accessibleRepositories = repositoryManager.findKnownRepositories(repository)
    if (accessibleRepositories.isEmpty()) return emptyList()

    return accessibleRepositories.map {
      uriProducer(it.repository.getWebURI(), commit.hash.asString()).let(HostedGitRepositoryReference::WebURI)
    }
  }

  fun findReferences(
    project: Project, repositoryManager: HostedGitRepositoriesManager<*>, virtualFile: VirtualFile, editor: Editor?,
    uriProducer: (repository: URI, revisionHash: String, relativePath: String, lineRange: IntRange?) -> URI
  ): List<HostedGitRepositoryReference> {
    val lineRange: IntRange? =
      editor?.takeIf { it.document.lineCount >= 1 }?.let {
        ReadAction.compute<IntRange?, Throwable> {
          val caret = it.caretModel.currentCaret
          val selectionStart = caret.selectionStart
          val begin = it.document.getLineNumber(selectionStart)

          val selectionEnd = caret.selectionEnd
          var end = it.document.getLineNumber(selectionEnd)

          if (it.document.getLineStartOffset(end) == selectionEnd) {
            end -= 1
          }
          begin..end
        }
      }

    return findReferences(project, repositoryManager, virtualFile, lineRange, uriProducer)
  }

  fun findReferences(
    project: Project, repositoryManager: HostedGitRepositoriesManager<*>, virtualFile: VirtualFile, lineRange: IntRange?,
    uriProducer: (repository: URI, revisionHash: String, relativePath: String, lineRange: IntRange?) -> URI
  ): List<HostedGitRepositoryReference> {
    if (virtualFile.isInLocalFileSystem) {
      val status = ChangeListManager.getInstance(project).getStatus(virtualFile)
      if (status == FileStatus.UNKNOWN || status == FileStatus.ADDED || status == FileStatus.IGNORED) return emptyList()
    }

    val filePath = VcsUtil.getFilePath(virtualFile)
    val repository = GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(filePath)
    if (repository == null) return emptyList()

    val accessibleRepositories = repositoryManager.findKnownRepositories(repository)
    if (accessibleRepositories.isEmpty()) return emptyList()

    val relativePath = VcsFileUtil.relativePath(repository.root, filePath)
    if (relativePath.isNullOrBlank()) return emptyList()

    return accessibleRepositories.map {
      HostedGitRepositoryReference.File(project, it.repository.getWebURI(), filePath, relativePath, lineRange, uriProducer)
    }
  }

}

interface HostedGitRepositoryReference {

  fun getName(): @Nls String

  fun buildWebURI(): URI?

  data class WebURI(private val uri: URI) : HostedGitRepositoryReference {
    override fun getName(): @NlsSafe String = uri.toString().replace('_', ' ')
    override fun buildWebURI(): URI = uri
  }

  data class File(
    private val project: Project,
    private val repository: URI,
    private val filePath: FilePath,
    private val relativePath: String,
    private val lineRange: IntRange?,
    private val uriProducer: (repository: URI, revisionHash: String, relativePath: String, lineRange: IntRange?) -> URI
  ) : HostedGitRepositoryReference {
    override fun getName(): @NlsSafe String = repository.toString().replace('_', ' ')

    override fun buildWebURI(): URI? {
      //TODO: check pushed
      val hash = getCurrentFileRevisionHash(project, filePath)
      if (hash == null) {
        VcsNotifier.getInstance(project).notifyError(
          GitNotificationIdsHolder.OPEN_IN_BROWSER_ERROR,
          GitBundle.message("open.in.browser.error"),
          GitBundle.message("open.in.browser.getting.revision.error")
        )
        return null
      }

      return uriProducer(repository, hash, relativePath, lineRange)
    }

    private fun getCurrentFileRevisionHash(project: Project, filePath: FilePath): String? {
      val revision = try {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
          ThrowableComputable {
            GitHistoryUtils.getCurrentRevision(project, filePath, "HEAD") as GitRevisionNumber?
          }, GitBundle.message("open.in.browser.getting.revision"), true, project)
      }
      catch (e: Exception) {
        null
      }
      return revision?.rev
    }
  }
}