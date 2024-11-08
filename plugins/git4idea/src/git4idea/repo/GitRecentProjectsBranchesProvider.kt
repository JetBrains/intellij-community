// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.github.benmanes.caffeine.cache.AsyncCacheLoader
import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.vcs.RecentProjectsBranchesProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.wm.IdeFrame
import com.intellij.util.application
import com.intellij.util.concurrency.AppExecutorUtil
import git4idea.GitUtil
import git4idea.branch.GitBranchUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.future.future
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.readText

internal class GitRecentProjectsBranchesProvider : RecentProjectsBranchesProvider {
  override fun getCurrentBranch(projectPath: String): String? = application.service<GitRecentProjectsBranchesService>().getCurrentBranch(projectPath)
}

@Service
internal class GitRecentProjectsBranchesService(val coroutineScope: CoroutineScope) : Disposable {
  private val recentProjectsTopic = application.messageBus.syncPublisher(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC)
  private val appMessageBusConnection = application.messageBus.simpleConnect()

  private val updateRecentProjectsSignal =
    MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val cache: AsyncLoadingCache<String, GitRecentProjectCachedBranch> = Caffeine.newBuilder()
    .refreshAfterWrite(REFRESH_IN)
    .expireAfterAccess(EXPIRE_IN)
    .executor(AppExecutorUtil.getAppExecutorService())
    .buildAsync(BranchesLoader())

  init {
    appMessageBusConnection.subscribe(ApplicationActivationListener.TOPIC, object : ApplicationActivationListener {
      override fun applicationActivated(ideFrame: IdeFrame) {
        if (ideFrame.project?.isDefault == true) {
          cache.synchronous().refreshAll(cache.asMap().keys)
        }
      }
    })

    coroutineScope.launch {
      @OptIn(FlowPreview::class)
      updateRecentProjectsSignal.debounce(50).collectLatest {
        withContext(Dispatchers.EDT) {
          recentProjectsTopic.change()
        }
      }
    }
  }

  fun getCurrentBranch(projectPath: String): String? {
    val branchFuture = cache.get(projectPath)
    return (branchFuture.getNow(GitRecentProjectCachedBranch.Unknown) as? GitRecentProjectCachedBranch.KnownBranch)?.branchName
  }

  override fun dispose() {
    appMessageBusConnection.disconnect()
    cache.synchronous().invalidateAll()
  }

  private inner class BranchesLoader : AsyncCacheLoader<String, GitRecentProjectCachedBranch> {
    override fun asyncLoad(key: String, executor: Executor) =
      loadBranch(key, null, executor)

    override fun asyncReload(key: String, oldValue: GitRecentProjectCachedBranch, executor: Executor) =
      loadBranch(key, oldValue, executor)

    private fun loadBranch(projectPath: String, previousValue: GitRecentProjectCachedBranch?, executor: Executor): CompletableFuture<GitRecentProjectCachedBranch>? =
      coroutineScope
        .future { loadBranch(previousValue, projectPath) }
        .whenCompleteAsync(
          { branch, _ ->
            if (branch != null && branch != previousValue) updateRecentProjectsSignal.tryEmit(Unit)
          }, executor
        )
  }

  companion object {
    private val REFRESH_IN = Duration.ofSeconds(30)
    private val EXPIRE_IN = Duration.ofSeconds(60)

    private val LOG = thisLogger()

    @VisibleForTesting
    internal suspend fun loadBranch(previousValue: GitRecentProjectCachedBranch?, projectPath: String): GitRecentProjectCachedBranch {
      if (previousValue == GitRecentProjectCachedBranch.Unknown) return previousValue

      return try {
        val headFile = previousValue?.headFilePath?.let(Path::of) ?: findGitHead(projectPath)
        getBranch(headFile)
      }
      catch (e: Exception) {
        LOG.warn("Failed to detect git branch", e)
        GitRecentProjectCachedBranch.Unknown
      }
    }

    private suspend fun getBranch(headFile: Path?): GitRecentProjectCachedBranch {
      if (headFile == null) return GitRecentProjectCachedBranch.Unknown

      val headFileContent = withContext(Dispatchers.IO) {
        if (Files.exists(headFile)) headFile.readText().trim() else null
      } ?: return GitRecentProjectCachedBranch.Unknown

      val targetRef =
        (if (GitRefUtil.parseHash(headFileContent) == null) GitRefUtil.getTarget(headFileContent) else null)
        ?: return GitRecentProjectCachedBranch.NotOnBranch(headFile.absolutePathString())

      return GitRecentProjectCachedBranch.KnownBranch(branchName = GitBranchUtil.stripRefsPrefix(targetRef), headFilePath = headFile.absolutePathString())
    }

    private fun findGitHead(projectPath: String): Path? {
      val gitRoot = findGitRootFor(Path(projectPath)) ?: return null
      // Note that git worktree scenario is not supported
      return gitRoot.resolve(GitUtil.DOT_GIT).resolve(GitUtil.HEAD)
    }

    private fun findGitRootFor(path: Path): Path? =
      generateSequence(path) { it.parent }.find { GitUtil.isGitRoot(it) }
  }
}

@VisibleForTesting
internal sealed class GitRecentProjectCachedBranch {
  open val headFilePath: String? = null

  data object Unknown : GitRecentProjectCachedBranch()
  data class NotOnBranch(override val headFilePath: String) : GitRecentProjectCachedBranch()
  data class KnownBranch(val branchName: String, override val headFilePath: String) : GitRecentProjectCachedBranch()
}
