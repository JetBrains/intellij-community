// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.Topic
import com.intellij.vcs.commit.CommitMessage
import com.intellij.vcs.commit.DefaultCommitMessagePolicy
import com.intellij.vcs.commit.DefaultCommitMessagePolicy.CommitMessageController
import com.intellij.vfs.AsyncVfsEventsListener
import com.intellij.vfs.AsyncVfsEventsPostProcessor
import git4idea.GitVcs
import git4idea.repo.GitRepoInfo
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryFiles.MERGE_MSG
import git4idea.repo.GitRepositoryManager
import git4idea.repo.GitRepositoryStateChangeListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicReference

internal class GitMergeCommitMessagePolicy: DefaultCommitMessagePolicy {
  override fun enabled(project: Project): Boolean = getSingleGitRepository(project) != null

  override fun getMessage(project: Project): CommitMessage? = GitMergeCommitMessageHolder.getInstance(project).getMessage()

  override fun initAsyncMessageUpdate(project: Project, controller: CommitMessageController, disposable: Disposable) {
    LOG.debug("Git merge commit message listener initialized")
    GitMergeCommitMessageChangedListener.subscribeController(project, controller, disposable)
  }

  companion object {
    private val LOG = Logger.getInstance(GitMergeCommitMessagePolicy::class.java)
  }
}

internal class GitMergeCommitMessageActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    GitMergeCommitMessageHolder.getInstance(project).initListeners()
  }
}

@Service(Service.Level.PROJECT)
internal class GitMergeCommitMessageHolder(
  private val project: Project,
  val coroutineScope: CoroutineScope,
) : AsyncVfsEventsListener, GitRepositoryStateChangeListener {

  private var currentMessage: AtomicReference<String?> = AtomicReference(null)

  fun getMessage(): GitMergeCommitMessage? = currentMessage.get()?.let { GitMergeCommitMessage(it) }

  fun initListeners() {
    AsyncVfsEventsPostProcessor.getInstance().addListener(this, coroutineScope)

    // Repository update events arrive faster than VFS refresh events.
    // However, merge message file content might still be missing
    val busConnection = project.messageBus.connect(coroutineScope)
    busConnection.subscribe(GitRepository.GIT_REPO_STATE_CHANGE, this)
  }

  override fun repositoryChanged(repository: GitRepository, previousInfo: GitRepoInfo, info: GitRepoInfo) {
    if (getSingleGitRepository(project) == null) return

    coroutineScope.launch {
      tryUpdateMergeCommitMessage(repository)
    }
  }

  override suspend fun filesChanged(events: List<VFileEvent>) {
    val repository = getSingleGitRepository(project)
    if (repository == null) {
      currentMessage.set(null)
      return
    }

    val maybeMergeMsgEvents = events.filter { it.file?.name == MERGE_MSG }
    if (maybeMergeMsgEvents.isEmpty()) return

    val repositoryFiles = repository.repositoryFiles
    if (maybeMergeMsgEvents.none { repositoryFiles.isMergeMessageFile(it.path) }) return

    checkCanceled()

    tryUpdateMergeCommitMessage(repository)
  }

  fun tryUpdateMergeCommitMessage(repository: GitRepository) {
    val newMessage = GitMergeCommitMessageReader.getInstance(project).read(repository)
    if (LOG.isDebugEnabled) {
      if (newMessage == null) {
        LOG.debug("Merge message file is missing.")
      } else {
        LOG.debug("Merge message file exists.")
      }
    }

    val oldValue = currentMessage.getAndSet(newMessage)
    if (newMessage != oldValue) {
      project.messageBus.syncPublisher(GitMergeCommitMessageChangedListener.TOPIC).messageUpdated(newMessage)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(GitMergeCommitMessageHolder::class.java)
    fun getInstance(project: Project) = project.service<GitMergeCommitMessageHolder>()
  }
}

private interface GitMergeCommitMessageChangedListener {
  fun messageUpdated(message: String?)

  companion object {
    private val LOG = Logger.getInstance(GitMergeCommitMessageChangedListener::class.java)

    @Topic.ProjectLevel
    val TOPIC: Topic<GitMergeCommitMessageChangedListener> =
      Topic(GitMergeCommitMessageChangedListener::class.java, Topic.BroadcastDirection.NONE, true)

    fun subscribeController(project: Project, controller: CommitMessageController, disposable: Disposable) {
      val busConnection = project.messageBus.connect(disposable)
      busConnection.subscribe(TOPIC, object : GitMergeCommitMessageChangedListener {
        override fun messageUpdated(message: String?) {
          runInEdt {
            if (message == null) {
              LOG.info("Merge commit message is irrelevant. Trying to restore previous message")
              controller.tryRestoreCommitMessage()
            }
            else {
              LOG.info("Merge commit message is set")
              controller.setCommitMessage(GitMergeCommitMessage(message))
            }
          }
        }
      })
    }
  }
}

@VisibleForTesting
internal class GitMergeCommitMessage(text: String): CommitMessage(text, disposable = true) {
  override fun equals(other: Any?): Boolean = other is GitMergeCommitMessage && text == other.text

  override fun hashCode(): Int = text.hashCode()
}

private fun getSingleGitRepository(project: Project): GitRepository? =
  if (ProjectLevelVcsManager.getInstance(project).singleVCS is GitVcs)
    GitRepositoryManager.getInstance(project).repositories.singleOrNull()
  else null
