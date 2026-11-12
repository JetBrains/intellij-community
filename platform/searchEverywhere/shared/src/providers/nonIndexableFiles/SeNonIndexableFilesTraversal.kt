// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.nonIndexableFiles

import com.intellij.find.DirectorySearchEngine.FileSearchCandidate
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.ConcurrentFileTraversal
import com.intellij.util.indexing.ConcurrentFileTraversal.TraversalItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration

/**
 * Holds the roots of one search of [SeNonIndexableFilesSearch] and the channel of its good matches.
 */
internal class SearchJobsState(traversal: ConcurrentFileTraversal) {
  val roots: Collection<TraversalItem> = traversal.roots
  private val pathFromRootResolver: PathFromRootResolver = PathFromRootResolver(roots.map { it.file })
  private val resultsChannel: Channel<Pair<FileSearchCandidate, Int>> = Channel(Channel.UNLIMITED)

  fun getPathFromRoot(file: FileSearchCandidate): String? = when (file) {
    is FileSearchCandidate.FromPath -> pathFromRootResolver.getPathFromRoot(file.path)
    is FileSearchCandidate.FromVirtualFile -> pathFromRootResolver.getPathFromRoot(file.file)
  }

  fun processItem(item: TraversalItem, handle: ParallelQueueProcessor<TraversalItem>): Boolean {
    return item.expand { children ->
      children.forEach(handle::queueSpawningWorkerJobIfNotAtLimit)
    }
  }

  fun emitResult(file: FileSearchCandidate, score: Int) {
    resultsChannel.trySend(file to score)
  }

  suspend fun collectResults(collector: suspend (VirtualFile, Int) -> Boolean): Boolean {
    for ((candidate, score) in resultsChannel) {
      val file = candidate.resolveVirtualFile() ?: continue
      if (!collector(file, score)) return false
    }
    return true
  }

  fun producerCompleted() {
    resultsChannel.close()
  }
}

/**
 * Gives the path of a file from the name of its outer non-indexable root, for example `root/sub/file.txt`.
 */
@ApiStatus.Internal
@VisibleForTesting
class PathFromRootResolver(roots: Collection<VirtualFile>) {
  private val rootNamesByPath: Map<String, String> = roots.associate { it.path to it.name }
  // "outer" here means that we remove nested roots, and getPathFromRoot will provide the longest possible path
  private val outerRoots: List<String> = buildList {
    for (root in rootNamesByPath.keys.sortedWith(FileUtil::comparePaths)) {
      if (isEmpty() || !FileUtil.startsWith(root, last())) add(root)
    }
  }

  /** The caller must pass an absolute [path] after calling [Path.normalize]. */
  fun getPathFromRoot(path: Path): String? {
    return getPathFromRoot(path.invariantSeparatorsPathString)
  }

  fun getPathFromRoot(file: VirtualFile): String? {
    return getPathFromRoot(file.path)
  }

  private fun getPathFromRoot(path: String): String? {
    val root = outerRoots.firstOrNull { FileUtil.startsWith(path, it) } ?: return null
    val relativePath = path.substring(root.length).removePrefix("/")
    val rootName = rootNamesByPath.getValue(root)
    return if (relativePath.isEmpty()) rootName else "${rootName}/$relativePath"
  }
}

/**
 * Runs a processor on the items of a queue in at most `jobsNumber` parallel jobs. The processor can add items to the queue.
 */
// Implementation note:
// this thing is optimized for speed of iteration, not
internal class ParallelQueueProcessor<TNode : Any> private constructor(
  val parentScope: CoroutineScope,
  val jobsNumber: Int,
  val queue: ConcurrentLinkedQueue<TNode>,
  val jobYieldTimeout: Long,
  val processorToBeParalleled: suspend (ParallelQueueProcessor<TNode>, TNode) -> Unit,
) {

  companion object {
    @JvmStatic
    fun <TNode : Any> createRunning(
      scope: CoroutineScope,
      jobsNumber: Int,
      initialItems: Collection<TNode>,
      workerJobYieldTimeout: Duration,
      processor: suspend (ParallelQueueProcessor<TNode>, TNode) -> Unit
    ): ParallelQueueProcessor<TNode> {
      return ParallelQueueProcessor(
        scope, jobsNumber, ConcurrentLinkedQueue<TNode>(), workerJobYieldTimeout.inWholeNanoseconds, processor
      ).apply {
        for (it in initialItems) {
          queueSpawningWorkerJobIfNotAtLimit(it)
        }
      }
    }
  }

  // This has to be lockfree-threadsafe because
  // (a) we add jobs dynamically
  // (b) the addition of jobs should be able to succeed in parallel without locking java thread sync (it is a JVM memory barrier, alas!)
  // because it happens in a hot path during iteration of files
  private val jobsRunning = AtomicInteger(0)
  private fun tryAcquirePermit(): Boolean {
    val alreadyRunningCount = jobsRunning.get()
    if (alreadyRunningCount >= jobsNumber) {
      return false
    }

    val incrementedCount = jobsRunning.incrementAndGet()
    if (incrementedCount <= jobsNumber) {
      return true
    }

    // lockfree shennanigans: we must handle every possible value of count here because it can be any value
    // this mechanism guarantees eventual de-contention
    // Consider:
    // 5 jobs are trying to start, and limit is 3;
    // all 5 have incremented the jobsRunning counter
    // then 3 have passed the increment check
    // two are left (incrementedCount == 4, incrementedCount == 5), jobsRunning is 5
    // every one of two needs to decrement
    // one of those will get decrementedCount == 4, another -- decrementedCount == 5
    // both should fail-out
    // BUT it could be that they are placed in contention with completion of other jobs
    // (that also decrements the counter)
    // hence we need to have the count be checked for updates...
    var decrementedCount = jobsRunning.decrementAndGet()
    while (true) {
      if (decrementedCount > jobsNumber) {
        return false
      }
      // Here we overshot the decrement due to contention, so it is like decrementedCount == 1
      // We need to get it back, but iff no-one contended or contended in an ABA manner
      // (this aquired the permit for the current job)
      val actualCount = jobsRunning.compareAndExchange(decrementedCount, decrementedCount + 1)
      if (actualCount == decrementedCount) {
        return true
      }

      // contention: loop again
      decrementedCount = actualCount
    }
  }

  private fun releasePermit() {
    jobsRunning.decrementAndGet()
  }

  private fun launchProcessingJob() {
    parentScope.launch(start = CoroutineStart.UNDISPATCHED) {
      val currentJob = coroutineContext.job
      do {
        if (!tryAcquirePermit()) return@launch
        try {
          // Drop the start == UNDISPATCHED status
          yield()
          // Pass the job to avoid the linked list access on coroutineContext.job
          dequeueAndProcess(currentJob)
        }
        catch (@Suppress("IncorrectCancellationExceptionHandling") e: CancellationException) {
          // This prohibits the "internal" cancellation of this job via a cancellation thrown by client code
          // The Exception propagates properly to the toplevel and dismantles the entire parent scope.
          // Must be a common idiom in platform code ;(
          currentJob.ensureActive()
          throw Exception("Unexpected cancellation while processing!", e)
        }
        finally {
          releasePermit()
        }
      // This *does* make it so that some jobs complete even though
      // some other still might queue more work items onto the queue.
      // That's why the queue method relaunches jobs up to maxJobsNumber
      // THE INVARIANT is that there is always at least ONE job doing work
      } while (queue.isNotEmpty())
    }
  }

  private suspend fun dequeueAndProcess(currentJob: Job) {
    var start = System.nanoTime()
    while (true) {
      val item = queue.poll() ?: return

      currentJob.ensureActive()
      // Ideologically it should pass a separate object here, but this is a hot path
      // and allocs are unwanted here
      processorToBeParalleled(this@ParallelQueueProcessor, item)

      // It is paramount that this exists when we are queued to some shared fixed-sized
      // thread-pool-backed Dispatcher; like Dispatchers.Default or Dispatchers.IO
      // Dispatchers.IO.limitedParallelism dispatchers are safe without it -- because
      // "limited" isn't really "limited" for them. read the KDoc for Dispatchers.IO.
      if (System.nanoTime() - start >= jobYieldTimeout) {
        yield()
        start = System.nanoTime()
      }
    }
  }

  fun queueSpawningWorkerJobIfNotAtLimit(node: TNode) {
    queue.add(node)

    if (jobsRunning.get() < jobsNumber) {
      // This should be enough to keep the throughput maxed-out
      launchProcessingJob()
    }
  }
}
