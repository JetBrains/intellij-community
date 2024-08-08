// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.RepositoryImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsScope
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager.Companion.getInstance
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.ObjectUtils
import com.intellij.vcs.log.Hash
import git4idea.*
import git4idea.branch.GitBranchesCollection
import git4idea.ignore.GitRepositoryIgnoredFilesHolder
import git4idea.status.GitStagingAreaHolder
import git4idea.telemetry.GitTelemetrySpan
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.util.*

class GitRepositoryImpl private constructor(
  project: Project,
  rootDir: VirtualFile,
  private val gitDir: VirtualFile,
  parentDisposable: Disposable
) : RepositoryImpl(project, rootDir, parentDisposable), GitRepository {

  private val vcs = GitVcs.getInstance(project)

  private val repositoryFiles = GitRepositoryFiles.createInstance(rootDir, gitDir)
  private val repositoryReader = GitRepositoryReader(repositoryFiles)

  private val stagingAreaHolder: GitStagingAreaHolder
  private val untrackedFilesHolder: GitUntrackedFilesHolder
  private val tagHolder: GitTagHolder

  @Volatile
  private var repoInfo: GitRepoInfo

  @Volatile
  private var recentCheckoutBranches = emptyList<GitLocalBranch>()

  private val coroutineScope = GitDisposable.getInstance(project).coroutineScope.childScope("GitRepositoryImpl")

  /**
   * @param rootDir Root of the repository (parent directory of '.git' file/directory).
   * @param gitDir  '.git' directory location. For worktrees - location of the 'main_repo/.git/worktrees/worktree_name/'.
   */
  init {
    stagingAreaHolder = GitStagingAreaHolder(this)

    untrackedFilesHolder = GitUntrackedFilesHolder(this)
    Disposer.register(this, untrackedFilesHolder)

    tagHolder = GitTagHolder(this)
    repoInfo = readRepoInfo()
    tagHolder.updateEnabled()
  }

  @Deprecated("Deprecated in Java")
  override fun getGitDir(): VirtualFile {
    return gitDir
  }

  override fun getRepositoryFiles(): GitRepositoryFiles {
    return repositoryFiles
  }

  override fun getStagingAreaHolder(): GitStagingAreaHolder {
    return stagingAreaHolder
  }

  override fun getUntrackedFilesHolder(): GitUntrackedFilesHolder {
    return untrackedFilesHolder
  }

  override fun getIgnoredFilesHolder(): GitRepositoryIgnoredFilesHolder {
    return untrackedFilesHolder.ignoredFilesHolder
  }

  override fun getTagHolder(): GitTagHolder {
    return tagHolder
  }

  override fun getCoroutineScope(): CoroutineScope {
    return coroutineScope
  }

  override fun getInfo(): GitRepoInfo {
    return repoInfo
  }

  override fun getCurrentBranch(): GitLocalBranch? {
    return repoInfo.currentBranch
  }

  override fun getCurrentRevision(): String? {
    return repoInfo.currentRevision
  }

  override fun getState(): Repository.State {
    return repoInfo.state
  }

  override fun getCurrentBranchName(): String? {
    val currentBranch = currentBranch
    return currentBranch?.name
  }

  override fun getVcs(): GitVcs {
    return vcs
  }

  override fun getSubmodules(): Collection<GitSubmoduleInfo> {
    return repoInfo.submodules
  }

  /**
   * @return local and remote branches in this repository.
   */
  override fun getBranches(): GitBranchesCollection {
    val info = repoInfo
    return GitBranchesCollection(info.localBranchesWithHashes, info.remoteBranchesWithHashes, recentCheckoutBranches)
  }

  override fun getRemotes(): Collection<GitRemote> {
    return repoInfo.remotes
  }

  override fun getBranchTrackInfos(): Collection<GitBranchTrackInfo> {
    return repoInfo.branchTrackInfos
  }

  override fun getBranchTrackInfo(localBranchName: String): GitBranchTrackInfo? {
    return repoInfo.branchTrackInfosMap[localBranchName]
  }

  override fun isRebaseInProgress(): Boolean {
    return state == Repository.State.REBASING
  }

  override fun isOnBranch(): Boolean {
    return repoInfo.isOnBranch
  }


  override fun update() {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val previousInfo = repoInfo
    repoInfo = readRepoInfo()
    notifyIfRepoChanged(this, previousInfo, repoInfo)
  }

  private fun readRepoInfo(): GitRepoInfo {
    return getInstance().getTracer(VcsScope).spanBuilder(GitTelemetrySpan.Repository.ReadGitRepositoryInfo.getName()).use { span ->
      span.setAttribute("repository", DvcsUtil.getShortRepositoryName(this))

      val configFile = repositoryFiles.configFile
      val config = GitConfig.read(configFile)
      repositoryFiles.updateCustomPaths(config.parseCore())

      val remotes = config.parseRemotes()
      val state = repositoryReader.readState(remotes)
      val isShallow = repositoryReader.hasShallowCommits()
      val trackInfos = config.parseTrackInfos(state.localBranches.keys, state.remoteBranches.keys)
      val hooksInfo = repositoryReader.readHooksInfo()
      val submoduleFile = File(VfsUtilCore.virtualToIoFile(root), ".gitmodules")
      val submodules = GitModulesFileReader().read(submoduleFile)
      val localBranches: Map<GitLocalBranch, Hash> = HashMap(state.localBranches)
      recentCheckoutBranches = collectRecentCheckoutBranches { branch: GitLocalBranch -> localBranches.containsKey(branch) }
      GitRepoInfo(currentBranch = state.currentBranch,
                  currentRevision = state.currentRevision,
                  state = state.state,
                  remotes = LinkedHashSet(remotes),
                  localBranchesWithHashes = localBranches,
                  remoteBranchesWithHashes = HashMap<GitRemoteBranch, Hash>(state.remoteBranches),
                  branchTrackInfos = LinkedHashSet(trackInfos),
                  submodules = submodules,
                  hooksInfo = hooksInfo,
                  isShallow = isShallow)
    }
  }

  override fun dispose() {
    super.dispose()
    coroutineScope.cancel()
  }

  override fun toLogString(): String {
    return "GitRepository $root : $repoInfo"
  }

  companion object {
    private val LOG = Logger.getInstance(GitRepositoryImpl::class.java)


    @JvmStatic
    @Deprecated("Use {@link GitRepositoryManager#getRepositoryForRoot} to obtain an instance of a Git repository.")
    fun getInstance(root: VirtualFile,
                    project: Project,
                    listenToRepoChanges: Boolean): GitRepository {
      val repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root)
      return ObjectUtils.notNull(repository) { createInstance(root, project, GitDisposable.getInstance(project)) }
    }


    @JvmStatic
    @ApiStatus.Internal
    @Deprecated("Use {@link #createInstance(VirtualFile, Project, Disposable)}")
    fun createInstance(root: VirtualFile,
                       project: Project,
                       parentDisposable: Disposable,
                       listenToRepoChanges: Boolean): GitRepository {
      return createInstance(root, project, parentDisposable)
    }

    /**
     * Creates a new instance of the GitRepository for the given Git root directory. <br></br>
     * Use [GitRepositoryManager.getRepositoryForRoot] if you need to obtain an instance
     */
    @JvmStatic
    @ApiStatus.Internal
    fun createInstance(root: VirtualFile,
                       project: Project,
                       parentDisposable: Disposable): GitRepository {
      val gitDir = Objects.requireNonNull(GitUtil.findGitDir(root))
      return createInstance(root, gitDir!!, project, parentDisposable)
    }

    @JvmStatic
    @ApiStatus.Internal
    fun createInstance(root: VirtualFile,
                       gitDir: VirtualFile,
                       project: Project,
                       parentDisposable: Disposable): GitRepository {
      ProgressManager.checkCanceled()

      val repository = GitRepositoryImpl(project, root, gitDir, parentDisposable)

      val updater = GitRepositoryUpdater(repository, repository.repositoryFiles)
      Disposer.register(repository, updater)

      GitRepositoryManager.getInstance(project).notifyListenersAsync(repository)
      return repository
    }

    private fun notifyIfRepoChanged(repository: GitRepository,
                                    previousInfo: GitRepoInfo,
                                    info: GitRepoInfo) {
      val project = repository.project
      if (!project.isDisposed && info != previousInfo) {
        GitRepositoryManager.getInstance(project).notifyListenersAsync(repository)
      }
    }
  }
}
