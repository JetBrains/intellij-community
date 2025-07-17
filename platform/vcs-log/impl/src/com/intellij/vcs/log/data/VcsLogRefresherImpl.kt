// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan.LogData.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.platform.vcs.impl.shared.telemetry.VcsTelemetrySpan
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.DataPack.ErrorDataPack
import com.intellij.vcs.log.data.SingleTaskController.SingleTask
import com.intellij.vcs.log.data.SingleTaskController.SingleTaskImpl
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex
import com.intellij.vcs.log.graph.GraphCommit
import com.intellij.vcs.log.graph.GraphCommitImpl
import com.intellij.vcs.log.impl.RequirementsImpl
import io.opentelemetry.api.trace.Span
import org.jetbrains.annotations.NonNls
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import kotlin.concurrent.Volatile

private val LOG = Logger.getInstance(VcsLogRefresherImpl::class.java)

internal open class VcsLogRefresherImpl(
  private val project: Project,
  private val storage: VcsLogStorage,
  private val providers: Map<VirtualFile, VcsLogProvider>,
  private val userRegistry: VcsUserRegistryImpl,
  private val index: VcsLogModifiableIndex,
  private val progress: VcsLogProgress,
  private val topCommitsDetailsCache: TopCommitsCache,
  private val dataPackUpdateHandler: Consumer<DataPack>,
  private val recentCommitCount: Int,
) : VcsLogRefresher, Disposable {
  private val singleTaskController: SingleTaskController<RefreshRequest, DataPack>
  private val initialized = AtomicBoolean()

  @Volatile
  final override var currentDataPack: DataPack = DataPack.EMPTY
    private set

  private val tracer = TelemetryManager.getInstance().getTracer(VcsScope)

  init {
    singleTaskController = object : SingleTaskController<RefreshRequest, DataPack>("permanent", this, Consumer { dataPack: DataPack ->
      if (dataPack !is SmallDataPack) {
        currentDataPack = dataPack
      }
      dataPackUpdateHandler.accept(dataPack)
    }) {
      override fun startNewBackgroundTask(): SingleTask {
        if (initialized.compareAndSet(false, true)) {
          return startNewBackgroundTask(MyInitializationTask())
        }
        return startNewBackgroundTask(MyRefreshTask(currentDataPack))
      }
    }
  }

  protected open fun startNewBackgroundTask(refreshTask: Task.Backgroundable): SingleTask {
    LOG.debug("Starting a background task...")
    val indicator = progress.createProgressIndicator(VcsLogData.DATA_PACK_REFRESH)
    val future = (ProgressManager.getInstance() as CoreProgressManager)
      .runProcessWithProgressAsynchronously(refreshTask, indicator, null)
    return SingleTaskImpl(future, indicator)
  }

  private fun loadRecentData(requirements: Map<VirtualFile, VcsLogProvider.Requirements>): LogInfo {
    return trace(ReadingRecentCommits) {
      val logInfo = LogInfo(storage)
      for ((root, requirements) in requirements) {
        val provider = requireNotNull(providers[root]) { "Cannot find provider for root $root" }
        trace(ReadingRecentCommitsInRoot) {
          val data = provider.readFirstBlock(root, requirements)
          logInfo.put(root, compactCommits(data.getCommits(), root))
          logInfo.put(root, data.getRefs())
          storeUsersAndDetails(data.getCommits())
        }
      }
      userRegistry.flush()
      index.scheduleIndex(false)
      logInfo
    }
  }

  override fun initialize() {
    if (initialized.get()) return
    singleTaskController.request(RefreshRequest.INITIALIZE)
  }

  override fun refresh(rootsToRefresh: Collection<VirtualFile>, optimized: Boolean) {
    if (!rootsToRefresh.isEmpty()) {
      singleTaskController.request(RefreshRequest(rootsToRefresh, optimized))
    }
  }

  private fun compactCommits(commits: List<TimedVcsCommit>, root: VirtualFile): List<GraphCommit<Int>> {
    return trace(CompactingCommits) {
      commits.map {
        compactCommit(it, root)
      }
    }
  }

  private fun compactCommit(commit: TimedVcsCommit, root: VirtualFile): GraphCommit<Int> {
    val parents = commit.getParents().map {
      storage.getCommitIndex(it, root)
    }

    val commitIdx = storage.getCommitIndex(commit.getId(), root)
    index.markForIndexing(commitIdx, root)
    return GraphCommitImpl.createIntCommit(commitIdx, parents, commit.getTimestamp())
  }

  private fun storeUsersAndDetails(metadatas: List<VcsCommitMetadata>) {
    for (detail in metadatas) {
      userRegistry.addUser(detail.getAuthor())
      userRegistry.addUser(detail.getCommitter())
    }
    topCommitsDetailsCache.storeDetails(metadatas)
  }

  override fun dispose() {
  }

  private inner class MyInitializationTask
    : Task.Backgroundable(project, VcsLogBundle.message("vcs.log.initial.loading.process"), false) {
    override fun run(indicator: ProgressIndicator) {
      singleTaskController.removeRequests(listOf(RefreshRequest.INITIALIZE))
      try {
        val result = readFirstBlock()
        singleTaskController.taskCompleted(result)
      }
      catch (e: ProcessCanceledException) {
        singleTaskController.taskCompleted(null)
        throw e
      }
    }

    private fun readFirstBlock(): DataPack {
      try {
        val dataPack = trace(Initializing) {
          val commitCountRequirements = CommitCountRequirements(recentCommitCount)
          val data = loadRecentData(providers.keys.associateWith { commitCountRequirements })
          val compoundList = multiRepoJoin(data.getCommits()).take(recentCommitCount)
          DataPack.build(compoundList, data.getRefs(), providers, storage, false)
        }

        singleTaskController.request(RefreshRequest.RELOAD_ALL) // build/rebuild the full log in background
        return dataPack
      }
      catch (e: ProcessCanceledException) {
        initialized.compareAndSet(true, false)
        throw e
      }
      catch (e: Exception) {
        LOG.info(e)
        return ErrorDataPack(e)
      }
    }
  }

  private inner class MyRefreshTask(private val initialDataPack: DataPack)
    : Task.Backgroundable(project, VcsLogBundle.message("vcs.log.refreshing.process"), false) {

    override fun run(indicator: ProgressIndicator) {
      LOG.debug("Refresh task started")
      indicator.setIndeterminate(true)

      var dataPack = initialDataPack
      val loadedInfo = LogInfo(storage)
      while (true) {
        val requests = singleTaskController.popRequests()

        var optimize = false
        val rootsToRefresh = mutableSetOf<VirtualFile>()

        for (request in requests) {
          if (request === RefreshRequest.RELOAD_ALL) {
            dataPack = DataPack.EMPTY
            rootsToRefresh.addAll(providers.keys.toList())
            optimize = false
            break
          }
          rootsToRefresh.addAll(request.rootsToRefresh)
          optimize = optimize || request.optimize
        }
        LOG.debug("Requests: $requests. roots to refresh: $rootsToRefresh")
        if (rootsToRefresh.isEmpty()) {
          singleTaskController.taskCompleted(dataPack)
          break
        }

        try {
          val providers = providers.filterKeys(rootsToRefresh::contains).values
          val supportsIncrementalRefresh = providers.all(VcsLogProperties.SUPPORTS_INCREMENTAL_REFRESH::getOrDefault)

          if (optimize && supportsIncrementalRefresh && isSmallDataPackEnabled) {
            val smallDataPack = buildSmallDataPack()
            if (smallDataPack !== DataPack.EMPTY) {
              dataPackUpdateHandler.accept(smallDataPack)
            }
          }

          dataPack = doRefresh(dataPack, loadedInfo, rootsToRefresh, supportsIncrementalRefresh)
        }
        catch (e: ProcessCanceledException) {
          singleTaskController.taskCompleted(null)
          throw e
        }
      }
    }

    private fun doRefresh(
      dataPack: DataPack, loadedInfo: LogInfo,
      roots: Collection<VirtualFile>, supportsIncrementalRefresh: Boolean,
    ): DataPack {
      try {
        val permanentGraph = if (dataPack.isFull) dataPack.permanentGraph else null
        if (permanentGraph == null || !supportsIncrementalRefresh) return loadFullLog()

        return trace(Refreshing) {
          val currentRefs = dataPack.refsModel.allRefsByRoot
          var commitCount = recentCommitCount
          repeat(2) {
            val requirements = prepareRequirements(roots, commitCount, currentRefs)
            val logInfo = loadRecentData(requirements)
            for (root in roots) {
              loadedInfo.put(root, logInfo.getCommits(root)!!)
              loadedInfo.put(root, logInfo.getRefs(root)!!)
            }

            val compoundLog = multiRepoJoin(loadedInfo.getCommits())
            val allNewRefs = currentRefs.toMutableMap().apply {
              replaceAll { root, refs ->
                loadedInfo.getRefs(root) ?: refs
              }
            }
            val joinedFullLog = join(permanentGraph.allCommits.toList(), compoundLog, currentRefs, allNewRefs)
            if (joinedFullLog != null) {
              return@trace DataPack.build(joinedFullLog, allNewRefs, providers, storage, true)
            }
            commitCount *= 5
          }
          // couldn't join => need to reload everything; if 5000 commits is still not enough, it's worth reporting:
          LOG.info("Couldn't join ${commitCount / 5} recent commits to the log (${permanentGraph.allCommits.size} commits)")
          loadFullLog()
        }
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: Exception) {
        LOG.info(e)
        return ErrorDataPack(e)
      }
    }

    private fun join(
      fullLog: List<GraphCommit<Int>>,
      recentCommits: List<GraphCommit<Int>>,
      previousRefs: Map<VirtualFile, CompressedRefs>,
      newRefs: Map<VirtualFile, CompressedRefs>,
    ): List<GraphCommit<Int>>? {
      if (fullLog.isEmpty()) return recentCommits

      return trace(JoiningNewAndOldCommits) {
        val prevRefIndices = previousRefs.values.flatMapTo(mutableSetOf()) { it.getCommits() }
        val newRefIndices = newRefs.values.flatMapTo(mutableSetOf()) { it.getCommits() }

        try {
          VcsLogJoiner<Int, GraphCommit<Int>>().addCommits(fullLog, prevRefIndices, recentCommits, newRefIndices).first
        }
        catch (e: VcsLogRefreshNotEnoughDataException) {
          // valid case: e.g. another developer merged a long-developed branch, or we just didn't pull for a long time
          LOG.info(e)
          null
        }
        catch (e: IllegalStateException) {
          // it happens from time to time, but we don't know why, and can hardly debug it.
          LOG.info(e)
          null
        }
      }
    }

    private fun loadFullLog(): DataPack =
      trace(LoadingFullLog) {
        val logInfo = readFullLogFromVcs()
        val graphCommits = multiRepoJoin(logInfo.getCommits())
        DataPack.build(graphCommits, logInfo.getRefs(), providers, storage, true)
      }

    private fun readFullLogFromVcs(): LogInfo =
      trace(ReadingAllCommits) {
        val logInfo = LogInfo(storage)
        for ((root, provider) in providers.entries) {
          trace(ReadingAllCommitsInRoot) { span ->
            span.setAttribute("rootName", root.getName())
            val graphCommits = mutableListOf<GraphCommit<Int>>()
            val data = provider.readAllHashes(root) {
              graphCommits.add(compactCommit(it, root))
            }
            logInfo.put(root, graphCommits)
            logInfo.put(root, data.getRefs())
            userRegistry.addUsers(data.getUsers())
          }
        }
        userRegistry.flush()
        index.scheduleIndex(true)
        logInfo
      }

    private fun buildSmallDataPack(): DataPack =
      trace(PartialRefreshing) {
        LOG.debug("Building a small datapack for $smallDataPackCommitsCount commits")
        try {
          val commitCount = smallDataPackCommitsCount
          val requirements = prepareRequirements(providers.keys, commitCount, null)
          val data = loadRecentData(requirements)
          val compoundList = multiRepoJoin(data.getCommits()).take(commitCount)
          SmallDataPack.build(compoundList, data.getRefs(), providers, storage)
        }
        catch (e: ProcessCanceledException) {
          throw e
        }
        catch (e: Exception) {
          LOG.info(e)
        }
        DataPack.EMPTY
      }

    private fun prepareRequirements(roots: Collection<VirtualFile>, commitCount: Int, prevRefs: Map<VirtualFile, CompressedRefs>?) =
      roots.associateWith { root ->
        val refs = prevRefs?.get(root)?.refs
        if (refs == null) {
          RequirementsImpl(commitCount, true, listOf<VcsRef>(), false)
        }
        else {
          RequirementsImpl(commitCount, true, refs)
        }
      }
  }

  private fun <T : GraphCommit<Int>> multiRepoJoin(commits: Collection<List<T>>): List<T> =
    trace(JoiningMultiRepoCommits) {
      VcsLogMultiRepoJoiner<Int, T>().join(commits)
    }

  private inline fun <T> trace(span: VcsTelemetrySpan, operation: (Span) -> T): T = tracer.spanBuilder(span.getName()).use(operation)
}

private val smallDataPackCommitsCount: Int
  get() = Registry.intValue("vcs.log.small.data.pack.commits.count")

private val isSmallDataPackEnabled: Boolean
  get() = smallDataPackCommitsCount > 0 && !ApplicationManager.getApplication().isUnitTestMode()

private open class RefreshRequest(
  val rootsToRefresh: Collection<VirtualFile>,
  val optimize: Boolean,
) {
  override fun toString(): String = "{$rootsToRefresh}"

  companion object {
    val RELOAD_ALL: RefreshRequest = object : RefreshRequest(emptyList(), false) {
      override fun toString(): @NonNls String {
        return "RELOAD_ALL"
      }
    }

    val INITIALIZE: RefreshRequest = object : RefreshRequest(emptyList(), false) {
      override fun toString(): @NonNls String {
        return "INITIALIZE"
      }
    }
  }
}


private class CommitCountRequirements(private val commitCount: Int) : VcsLogProvider.Requirements {
  override fun getCommitCount(): Int = commitCount
}

private class LogInfo(private val storage: VcsLogStorage) {
  private val refsByRoot = HashMap<VirtualFile, CompressedRefs>()
  private val commitsByRoot = HashMap<VirtualFile, List<GraphCommit<Int>>>()

  fun put(root: VirtualFile, commits: List<GraphCommit<Int>>) {
    commitsByRoot[root] = commits
  }

  fun put(root: VirtualFile, refs: Set<VcsRef>) {
    refsByRoot[root] = CompressedRefs(refs, storage)
  }

  fun put(root: VirtualFile, refs: CompressedRefs) {
    refsByRoot[root] = refs
  }

  fun getCommits(): Collection<List<GraphCommit<Int>>> = commitsByRoot.values

  fun getCommits(root: VirtualFile): List<GraphCommit<Int>>? = commitsByRoot[root]

  fun getRefs(): Map<VirtualFile, CompressedRefs> = refsByRoot.toMap()

  fun getRefs(root: VirtualFile): CompressedRefs? = refsByRoot[root]
}