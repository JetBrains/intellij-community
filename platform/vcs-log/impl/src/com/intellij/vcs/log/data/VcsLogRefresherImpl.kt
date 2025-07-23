// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan.LogData.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.vcs.log.TimedVcsCommit
import com.intellij.vcs.log.VcsLogProperties
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.data.DataPack.ErrorDataPack
import com.intellij.vcs.log.data.util.trace
import com.intellij.vcs.log.graph.GraphCommit
import com.intellij.vcs.log.graph.GraphCommitImpl
import com.intellij.vcs.log.impl.RequirementsImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.TestOnly
import java.util.function.Consumer
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

private val LOG = Logger.getInstance(VcsLogRefresherImpl::class.java)

internal class VcsLogRefresherImpl(
  parentCs: CoroutineScope,
  private val storage: VcsLogStorage,
  private val providers: Map<VirtualFile, VcsLogProvider>,
  private val progress: VcsLogProgress,
  private val commitDataConsumer: VcsLogCommitDataConsumer?,
  private val dataPackUpdateHandler: Consumer<DataPack>,
  private val recentCommitCount: Int,
) : VcsLogRefresher {
  private val refreshRequests = Channel<RefreshRequest>(Channel.UNLIMITED)
  private val refresherJob: Job

  @TestOnly
  var refreshBatchJobConsumer: ((Job) -> Unit)? = null

  @Volatile
  override var currentDataPack: DataPack = DataPack.EMPTY
    private set(value) {
      field = value
      LOG.debug("New data pack received: $value")
      dataPackUpdateHandler.accept(value)
    }

  private val tracer = TelemetryManager.getInstance().getTracer(VcsScope)

  init {
    refresherJob = parentCs.launch(Dispatchers.Default + CoroutineName("Vcs Log Refresher"), CoroutineStart.LAZY) {
      try {
        currentDataPack = loadFirstBlock()
        refreshRequests.send(RefreshRequest.ReloadAll) // build/rebuild the full log in background
      }
      catch (ce: CancellationException) {
        throw ce
      }
      catch (e: Exception) {
        LOG.info("Failed to load initial data", e)
        currentDataPack = ErrorDataPack(e)
      }

      while (true) {
        checkCanceled()
        supervisorScope { // don't cancel the request processing on processing errors
          val request = refreshRequests.receive()
          val requests = mutableListOf(request)

          launch {
            progress.runWithProgress(VcsLogData.DATA_PACK_REFRESH) {
              var dataPack = currentDataPack
              val logInfo = LogInfo()
              while (true) {
                checkCanceled()
                refreshRequests.receiveAll(requests::add)
                if (requests.isEmpty()) {
                  break
                }
                LOG.debug("Refresh requests: $requests")

                val cumulativeRequest = requests.fold()
                requests.clear()
                LOG.debug("Cumulative refresh request: $cumulativeRequest")
                dataPack = handleRequest(dataPack, logInfo, cumulativeRequest)
              }

              if (dataPack !== currentDataPack) {
                currentDataPack = dataPack
              }
            }
          }.also {
            refreshBatchJobConsumer?.invoke(it)
          }
        }
      }
    }
  }

  private suspend fun handleRequest(currentDataPack: DataPack, logInfo: LogInfo, request: RefreshRequest): DataPack {
    val rootsToRefresh: Set<VirtualFile>
    val optimize: Boolean
    val dataPack: DataPack
    when (request) {
      is RefreshRequest.RefreshRoots -> {
        if (request.rootsToRefresh.isEmpty()) {
          LOG.debug("No roots to refresh")
          return currentDataPack
        }

        rootsToRefresh = request.rootsToRefresh
        optimize = request.optimize
        dataPack = currentDataPack
      }
      RefreshRequest.ReloadAll -> {
        rootsToRefresh = providers.keys
        optimize = false
        dataPack = DataPack.EMPTY
        logInfo.clear()
      }
    }

    LOG.debug("Refreshing roots: $rootsToRefresh")
    val providers = providers.filterKeys(rootsToRefresh::contains).values
    val supportsIncrementalRefresh = providers.all(VcsLogProperties.SUPPORTS_INCREMENTAL_REFRESH::getOrDefault)
    if (optimize && supportsIncrementalRefresh && isSmallDataPackEnabled) {
      val smallDataPack = loadSmallDataPack()
      if (smallDataPack !== DataPack.EMPTY) {
        dataPackUpdateHandler.accept(smallDataPack)
      }
    }

    checkCanceled()
    val newDataPack = try {
      if (!dataPack.isFull || !supportsIncrementalRefresh) {
        loadFullLog()
      }
      else {
        loadUpdatedDataPack(dataPack, logInfo, rootsToRefresh)
        ?: loadFullLog()
      }
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      LOG.info(e)
      ErrorDataPack(e)
    }
    return newDataPack
  }

  override fun initialize() {
    refresherJob.start()
  }

  override fun refresh(rootsToRefresh: Collection<VirtualFile>, optimized: Boolean) {
    refresherJob.start()
    if (!rootsToRefresh.isEmpty()) {
      val sent = refreshRequests.trySend(RefreshRequest.RefreshRoots(rootsToRefresh.toSet(), optimized))
      if (!sent.isSuccess) {
        LOG.error("Failed to send a refresh request")
      }
    }
  }

  private suspend fun loadFirstBlock(): DataPack =
    tracer.trace(Initializing) {
      LOG.debug("Loading the first block")
      val commitCountRequirements = CommitCountRequirements(recentCommitCount)
      val data = loadRecentData(providers.keys.associateWith { commitCountRequirements })
      LOG.trace("First block loaded")
      checkCanceled()
      val compoundList = multiRepoJoin(data.getCommits()).take(recentCommitCount)
      LOG.trace("First block joined")
      checkCanceled()
      DataPack.build(compoundList, data.getRefs(), providers, storage, false).also {
        LOG.trace("First block built")
      }
    }

  private suspend fun loadUpdatedDataPack(dataPack: DataPack, loadedInfo: LogInfo, roots: Collection<VirtualFile>): DataPack? =
    tracer.trace(Refreshing) {
      LOG.debug("Loading the recent data for roots $roots")
      val permanentGraph = dataPack.permanentGraph
      val currentRefs = dataPack.refsModel.allRefsByRoot
      var commitCount = recentCommitCount
      repeat(2) {
        val requirements = prepareRequirements(roots, commitCount, currentRefs)
        val logInfo = loadRecentData(requirements)
        LOG.trace("Recent log loaded")
        checkCanceled()
        for (root in roots) {
          loadedInfo.put(root, logInfo.getCommits(root)!!)
          loadedInfo.put(root, logInfo.getRefs(root)!!)
        }

        val compoundLog = multiRepoJoin(loadedInfo.getCommits())
        checkCanceled()
        val allNewRefs = currentRefs.toMutableMap().apply {
          replaceAll { root, refs ->
            loadedInfo.getRefs(root) ?: refs
          }
        }
        val joinedFullLog = join(permanentGraph.allCommits.toList(), compoundLog, currentRefs, allNewRefs)
        LOG.trace("Recent log joined")
        if (joinedFullLog != null) {
          return@trace DataPack.build(joinedFullLog, allNewRefs, providers, storage, true).also {
            LOG.trace("Recent log built")
          }
        }
        commitCount *= 5
      }
      // couldn't join => need to reload everything; if 5000 commits is still not enough, it's worth reporting:
      LOG.info("Couldn't join ${commitCount / 5} recent commits to the log (${permanentGraph.allCommits.size} commits)")
      return@trace null
    }

  private suspend fun loadFullLog(): DataPack =
    tracer.trace(LoadingFullLog) {
      LOG.debug("Loading the full log")
      val logInfo = readFullLogFromVcs()
      LOG.trace("Full log loaded")
      checkCanceled()
      val graphCommits = multiRepoJoin(logInfo.getCommits())
      LOG.trace("Full log joined")
      checkCanceled()
      DataPack.build(graphCommits, logInfo.getRefs(), providers, storage, true).also {
        LOG.trace("Full log built")
      }
    }

  private suspend fun loadSmallDataPack(): DataPack =
    tracer.trace(PartialRefreshing) {
      LOG.debug("Loading a small datapack for $smallDataPackCommitsCount commits")
      try {
        val commitCount = smallDataPackCommitsCount
        val requirements = prepareRequirements(providers.keys, commitCount, null)
        val data = loadRecentData(requirements)
        LOG.trace("Small pack loaded")
        val compoundList = multiRepoJoin(data.getCommits()).take(commitCount)
        LOG.trace("Small pack joined")
        SmallDataPack.build(compoundList, data.getRefs(), providers, storage).also {
          LOG.trace("Small pack built")
        }
      }
      catch (ce: CancellationException) {
        throw ce
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

  private suspend fun loadRecentData(requirements: Map<VirtualFile, VcsLogProvider.Requirements>): LogInfo =
    tracer.trace(ReadingRecentCommits) {
      LOG.debug("Loading the recent data by requirements $requirements")
      val logInfo = LogInfo()
      for ((root, requirements) in requirements) {
        LOG.trace("Loading recent data for root $root with requirements $requirements")
        val provider = requireNotNull(providers[root]) { "Cannot find provider for root $root" }
        tracer.trace(ReadingRecentCommitsInRoot) {
          it.setAttribute("rootName", root.getName())
          val data = withContext(Dispatchers.IO) {
            coroutineToIndicator {
              provider.readFirstBlock(root, requirements)
            }
          }
          LOG.trace("Recent data loaded")
          checkCanceled()
          val commits = tracer.trace(CompactingCommits) {
            data.getCommits().map {
              compactCommit(it, root)
            }
          }
          LOG.trace("Recent commits compacted")
          checkCanceled()
          logInfo.put(root, commits)
          logInfo.put(root, CompressedRefs(data.getRefs(), storage))

          val users = buildSet {
            for (metadata in data.getCommits()) {
              add(metadata.author)
              add(metadata.committer)
            }
          }
          LOG.trace("Storing recent data: ${commits.size} commits, ${users.size} users")
          commitDataConsumer?.storeData(root, commits, users)
          commitDataConsumer?.storeRecentDetails(data.getCommits())
        }
      }
      LOG.trace("Recent data flushing")
      commitDataConsumer?.flushData(onFullReload = false)
      logInfo
    }

  private suspend fun readFullLogFromVcs(): LogInfo =
    tracer.trace(ReadingAllCommits) {
      val logInfo = LogInfo()
      for ((root, provider) in providers.entries) {
        LOG.trace("Loading the full data for root $root")
        tracer.trace(ReadingAllCommitsInRoot) { span ->
          span.setAttribute("rootName", root.getName())
          val graphCommits = mutableListOf<GraphCommit<Int>>()
          val data = withContext(Dispatchers.IO) {
            coroutineToIndicator {
              provider.readAllHashes(root) {
                graphCommits.add(compactCommit(it, root))
              }
            }
          }
          LOG.trace("Full data loaded and compacted")
          logInfo.put(root, graphCommits)
          logInfo.put(root, CompressedRefs(data.getRefs(), storage))

          LOG.trace("Storing full data: ${graphCommits.size} commits, ${data.getUsers().size} users")
          commitDataConsumer?.storeData(root, graphCommits, data.getUsers())
        }
      }
      LOG.trace("Full data flushing")
      commitDataConsumer?.flushData(onFullReload = true)
      logInfo
    }

  private fun compactCommit(commit: TimedVcsCommit, root: VirtualFile): GraphCommit<Int> {
    val commitIdx = storage.getCommitIndex(commit.getId(), root)
    val parents = commit.getParents().map { storage.getCommitIndex(it, root) }
    return GraphCommitImpl.createIntCommit(commitIdx, parents, commit.getTimestamp())
  }

  private fun join(
    fullLog: List<GraphCommit<Int>>,
    recentCommits: List<GraphCommit<Int>>,
    previousRefs: Map<VirtualFile, CompressedRefs>,
    newRefs: Map<VirtualFile, CompressedRefs>,
  ): List<GraphCommit<Int>>? {
    if (fullLog.isEmpty()) return recentCommits

    return tracer.trace(JoiningNewAndOldCommits) {
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

  private fun <T : GraphCommit<Int>> multiRepoJoin(commits: Collection<List<T>>): List<T> =
    tracer.trace(JoiningMultiRepoCommits) {
      VcsLogMultiRepoJoiner<Int, T>().join(commits)
    }
}

private val smallDataPackCommitsCount: Int
  get() = Registry.intValue("vcs.log.small.data.pack.commits.count")

private val isSmallDataPackEnabled: Boolean
  get() = smallDataPackCommitsCount > 0 && !ApplicationManager.getApplication().isUnitTestMode()

private sealed interface RefreshRequest {
  data class RefreshRoots(
    val rootsToRefresh: Set<VirtualFile>,
    val optimize: Boolean,
  ) : RefreshRequest

  object ReloadAll : RefreshRequest
}


private data class CommitCountRequirements(private val commitCount: Int) : VcsLogProvider.Requirements {
  override fun getCommitCount(): Int = commitCount
}

private class LogInfo {
  private val refsByRoot = HashMap<VirtualFile, CompressedRefs>()
  private val commitsByRoot = HashMap<VirtualFile, List<GraphCommit<Int>>>()

  fun put(root: VirtualFile, commits: List<GraphCommit<Int>>) {
    commitsByRoot[root] = commits
  }

  fun put(root: VirtualFile, refs: CompressedRefs) {
    refsByRoot[root] = refs
  }

  fun getCommits(): Collection<List<GraphCommit<Int>>> = commitsByRoot.values

  fun getCommits(root: VirtualFile): List<GraphCommit<Int>>? = commitsByRoot[root]

  fun getRefs(): Map<VirtualFile, CompressedRefs> = refsByRoot.toMap()

  fun getRefs(root: VirtualFile): CompressedRefs? = refsByRoot[root]

  fun clear() {
    refsByRoot.clear()
    commitsByRoot.clear()
  }
}

/**
 * Receive all immediately available elements from the channel
 */
private inline fun <T> Channel<T>.receiveAll(consumer: (T) -> Unit) {
  var nextItem: T?
  do {
    nextItem = tryReceive().getOrNull()
    if (nextItem != null) {
      consumer(nextItem)
    }
  }
  while (nextItem != null)
}

private fun List<RefreshRequest>.fold(): RefreshRequest {
  val roots = mutableSetOf<VirtualFile>()
  var optimize = false
  val list = this
  for (request in list) {
    when (request) {
      is RefreshRequest.RefreshRoots -> {
        roots.addAll(request.rootsToRefresh)
        optimize = optimize || request.optimize
      }
      RefreshRequest.ReloadAll -> {
        return RefreshRequest.ReloadAll
      }
    }
  }
  return RefreshRequest.RefreshRoots(roots, optimize)
}