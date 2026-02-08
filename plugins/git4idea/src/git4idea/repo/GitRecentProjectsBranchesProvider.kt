// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.github.benmanes.caffeine.cache.AsyncCacheLoader
import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.vcs.RecentProjectsBranchesProvider
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.wm.IdeFrame
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.util.application
import git4idea.GitUtil
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.util.CaffeineUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
  override fun getCurrentBranch(projectPath: String, nameIsDistinct: Boolean): String? {
    return application.service<GitRecentProjectsBranchesService>().getCurrentBranch(projectPath, nameIsDistinct)
  }
}

internal enum class RecentProjectsShowBranchMode {
  NEVER {
    override fun toString(): String = GitBundle.message("git.recent.projects.show.branch.mode.never")
    override fun shouldShow(nameIsDistinct: Boolean): Boolean = false
  },
  DUPLICATE_NAMES {
    override fun toString(): String = GitBundle.message("git.recent.projects.show.branch.mode.for.duplicate.names")
    override fun shouldShow(nameIsDistinct: Boolean): Boolean = !nameIsDistinct
  },
  ALWAYS {
    override fun toString(): String = GitBundle.message("git.recent.projects.show.branch.mode.always")
    override fun shouldShow(nameIsDistinct: Boolean): Boolean = true
  };

  abstract fun shouldShow(nameIsDistinct: Boolean): Boolean
}

@Service
internal class GitRecentProjectsBranchesService(private val coroutineScope: CoroutineScope) {
  private val updateRecentProjectsSignal = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val cache: AsyncLoadingCache<String, GitRecentProjectCachedBranch> = CaffeineUtil.withIoExecutor()
    .refreshAfterWrite(REFRESH_IN)
    .expireAfterAccess(EXPIRE_IN)
    .buildAsync(BranchesLoader())

  init {
    application.messageBus.connect(coroutineScope).subscribe(ApplicationActivationListener.TOPIC, object : ApplicationActivationListener {
      override fun applicationActivated(ideFrame: IdeFrame) {
        if (ideFrame.project?.isDefault == true) {
          cache.synchronous().refreshAll(cache.asMap().keys)
        }
      }
    })

    coroutineScope.launch {
      val recentProjectsTopic = application.messageBus.syncPublisher(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC)
      @OptIn(FlowPreview::class)
      updateRecentProjectsSignal.debounce(50).collectLatest {
        withContext(Dispatchers.EDT) {
          recentProjectsTopic.change()
        }
      }
    }
  }

  fun getCurrentBranch(projectPath: String, nameIsDistinct: Boolean): String? {
    val showBranchMode = AdvancedSettings.getEnum("git.recent.projects.show.branch", RecentProjectsShowBranchMode::class.java)
    if (!showBranchMode.shouldShow(nameIsDistinct)) {
      return null
    }

    // IJPL-194035
    // Avoid greedy I/O under non-local projects. For example, in the case of WSL:
    //	1.	it may trigger Ijent initialization for each recent project
    //	2.	with Ijent disabled, performance may degrade further — 9P is very slow and could lead to UI freezes
    if (Path(projectPath).getEelDescriptor() != LocalEelDescriptor) {
      return null
    }
    val branchFuture = cache.get(projectPath)
    return (branchFuture.getNow(GitRecentProjectCachedBranch.Unknown) as? GitRecentProjectCachedBranch.KnownBranch)?.branchName
  }

  private inner class BranchesLoader : AsyncCacheLoader<String, GitRecentProjectCachedBranch> {
    override fun asyncLoad(key: String, executor: Executor) = loadBranch(projectPath = key, previousValue = null, executor = executor)

    override fun asyncReload(key: String, oldValue: GitRecentProjectCachedBranch, executor: Executor): CompletableFuture<GitRecentProjectCachedBranch> {
      return loadBranch(projectPath = key, previousValue = oldValue, executor = executor)
    }

    private fun loadBranch(
      projectPath: String,
      previousValue: GitRecentProjectCachedBranch?,
      executor: Executor,
    ): CompletableFuture<GitRecentProjectCachedBranch> {
      return coroutineScope
        .future { loadBranch(previousValue, projectPath) }
        .whenCompleteAsync(
          { branch, _ ->
            if (branch != null && branch != previousValue) updateRecentProjectsSignal.tryEmit(Unit)
          }, executor
        )
    }
  }

  companion object {
    private val REFRESH_IN = Duration.ofSeconds(30)
    private val EXPIRE_IN = Duration.ofSeconds(60)

    private val INVALID = ".invalid"

    private val LOG = thisLogger()

    @VisibleForTesting
    internal suspend fun loadBranch(previousValue: GitRecentProjectCachedBranch?, projectPath: String): GitRecentProjectCachedBranch {
      if (previousValue == GitRecentProjectCachedBranch.Unknown) {
        return previousValue
      }

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

      val branchName = GitBranchUtil.stripRefsPrefix(targetRef)
      if (branchName == INVALID) return GitRecentProjectCachedBranch.Invalid

      return GitRecentProjectCachedBranch.KnownBranch(branchName = branchName, headFilePath = headFile.absolutePathString())
    }

    private suspend fun findGitHead(projectPath: String): Path? = withContext(Dispatchers.IO) {
      findGitDir(Path(projectPath))?.resolve(GitUtil.HEAD)?.takeIf { Files.exists(it) }
    }

    private fun findGitDir(path: Path): Path? =
      generateSequence(path) { it.parent }.mapNotNull { GitUtil.findGitDir(it) }.firstOrNull()
  }
}

@VisibleForTesting
internal sealed class GitRecentProjectCachedBranch {
  open val headFilePath: String? = null

  data object Unknown : GitRecentProjectCachedBranch()

  /**
   * e.g reftable format is used
   */
  data object Invalid : GitRecentProjectCachedBranch()
  data class NotOnBranch(override val headFilePath: String) : GitRecentProjectCachedBranch()
  data class KnownBranch(val branchName: String, override val headFilePath: String) : GitRecentProjectCachedBranch()
}
