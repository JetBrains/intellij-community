// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.RepositoryImpl
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsScope
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager.Companion.getInstance
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import git4idea.GitDisposable
import git4idea.GitLocalBranch
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.branch.GitBranchesCollection
import git4idea.ignore.GitRepositoryIgnoredFilesHolder
import git4idea.merge.GitResolvedMergeConflictsFilesHolder
import git4idea.remoteApi.rhizome.GitRepositoryEntitiesStorage
import git4idea.status.GitStagingAreaHolder
import git4idea.telemetry.GitTelemetrySpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus

/**
 * @param rootDir Root of the repository (parent directory of '.git' file/directory).
 * @param gitDir  '.git' directory location. For worktrees - location of the 'main_repo/.git/worktrees/worktree_name/'.
 */
class GitRepositoryImpl private constructor(
  project: Project,
  rootDir: VirtualFile,
  private val gitDir: VirtualFile,
  parentDisposable: Disposable,
) : RepositoryImpl(project, rootDir, parentDisposable), GitRepository {
  private val rpcId = RepositoryId(projectId = project.projectId(), rootPath = root.rpcId())

  private val vcs = GitVcs.getInstance(project)

  private val repositoryFiles = GitRepositoryFiles.createInstance(rootDir, gitDir)
  private val repositoryReader = GitRepositoryReader(project, repositoryFiles)

  private val stagingAreaHolder: GitStagingAreaHolder
  private val untrackedFilesHolder: GitUntrackedFilesHolder
  private val resolvedFilesHolder: GitResolvedMergeConflictsFilesHolder
  private val tagHolder: GitTagHolder

  @Volatile
  private var repoInfo: GitRepoInfo

  @Volatile
  private var recentCheckoutBranches = emptyList<GitLocalBranch>()

  private val coroutineScope = GitDisposable.getInstance(project).coroutineScope.childScope("GitRepositoryImpl")

  /**
   * @see [git4idea.repo.GitRepositoryImpl.createInstance]
   * @see [git4idea.repo.GitRepositoryUpdater.installListeners]
   */
  init {
    stagingAreaHolder = GitStagingAreaHolder(this)

    untrackedFilesHolder = GitUntrackedFilesHolder(this)
    Disposer.register(this, untrackedFilesHolder)

    resolvedFilesHolder = GitResolvedMergeConflictsFilesHolder(this)
    Disposer.register(this, resolvedFilesHolder)

    tagHolder = GitTagHolder(this)
    repoInfo = readRepoInfo()
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

  override fun getResolvedConflictsFilesHolder(): GitResolvedMergeConflictsFilesHolder {
    return resolvedFilesHolder
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
    GitRepositoryEntitiesStorage.getInstance(project).runRepoSync(this, false).get()
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

      val remoteBranches = state.remoteBranches
      val localBranches = state.localBranches
      val trackInfos = config.parseTrackInfos(state.localBranches.keys, state.remoteBranches.keys)

      val hooksInfo = repositoryReader.readHooksInfo()
      val submoduleFile = root.toNioPath().resolve(".gitmodules")
      val submodules = GitModulesFileReader().read(submoduleFile)
      recentCheckoutBranches = collectRecentCheckoutBranches(project, root) { branch: GitLocalBranch -> localBranches.containsKey(branch) }
      GitRepoInfo(currentBranch = state.currentBranch,
                  currentRevision = state.currentRevision,
                  state = state.state,
                  remotes = remotes,
                  localBranchesWithHashes = localBranches,
                  remoteBranchesWithHashes = remoteBranches,
                  branchTrackInfos = trackInfos,
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

  override fun getRpcId(): RepositoryId {
    return rpcId
  }

  companion object {
    private val LOG = Logger.getInstance(GitRepositoryImpl::class.java)

    @JvmStatic
    @Deprecated("Use {@link GitRepositoryManager#getRepositoryForRoot} to obtain an instance of a Git repository.")
    fun getInstance(
      root: VirtualFile,
      project: Project,
      listenToRepoChanges: Boolean,
    ): GitRepository {
      val repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root)
      return repository ?: createInstance(root, project, GitDisposable.getInstance(project))
    }


    @JvmStatic
    @ApiStatus.Internal
    @Deprecated("Use {@link #createInstance(VirtualFile, Project, Disposable)}")
    fun createInstance(
      root: VirtualFile,
      project: Project,
      parentDisposable: Disposable,
      listenToRepoChanges: Boolean,
    ): GitRepository {
      return createInstance(root, project, parentDisposable)
    }

    /**
     * Creates a new instance of the GitRepository for the given Git root directory. <br></br>
     * Use [GitRepositoryManager.getRepositoryForRoot] if you need to obtain an instance
     */
    @JvmStatic
    @ApiStatus.Internal
    fun createInstance(
      root: VirtualFile,
      project: Project,
      parentDisposable: Disposable,
    ): GitRepository {
      val gitDir = GitUtil.findGitDir(root) ?: error("Git directory not found for $root")
      return createInstance(root, gitDir, project, parentDisposable)
    }

    @JvmStatic
    @ApiStatus.Internal
    fun createInstance(
      root: VirtualFile,
      gitDir: VirtualFile,
      project: Project,
      parentDisposable: Disposable,
    ): GitRepository {
      ProgressManager.checkCanceled()
      return GitRepositoryImpl(project, root, gitDir, parentDisposable).apply {
        val initialRepoInfo = repoInfo
        val updater = GitRepositoryUpdater(this, this.repositoryFiles)
        updater.installListeners()
        GitRepositoryEntitiesStorage.getInstance(project).runRepoSync(this, true).get()
        notifyIfRepoChanged(this, null, initialRepoInfo)
        this.untrackedFilesHolder.invalidate()
        this.resolvedConflictsFilesHolder.invalidate()
      }
    }

    private fun notifyIfRepoChanged(repository: GitRepository, previousInfo: GitRepoInfo?, info: GitRepoInfo) {
      val project = repository.project
      if (!project.isDisposed && info != previousInfo) {
        GitRepositoryManager.getInstance(project).notifyListenersAsync(repository, previousInfo, info)
        LOG.debug("Repository $repository changed")
      }
    }
  }
}
