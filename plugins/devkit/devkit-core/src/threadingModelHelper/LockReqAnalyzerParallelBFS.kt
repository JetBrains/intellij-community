// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.concurrency.virtualThreads.inVirtualThread
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqPhaseTimer.Phase
import java.io.IOException
import java.nio.file.Path
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.measureTime

/**
 * The main controller of call graph traversal.
 *
 * The analysis is performed in parallel, with priority queue maintaining bfs-like traversal.
 * A major challenge here is the determinism of the operation for given arguments -- due to parallel processing, the order of arrival of
 * nodes to the processing functions is undefined, and we need to account for possibility to see the same node under different execution paths that have different lengths.
 */
class LockReqAnalyzerParallelBFS {

  companion object {
    private val LOG: Logger = Logger.getInstance(LockReqAnalyzerParallelBFS::class.java)

    /**
     * The default directory for the traversal statistics reports.
     */
    fun defaultStatisticsDir(): Path = PathManager.getLogDir().resolve("lock-req-analysis")
  }

  private data class TraversalContext(
    val config: AnalysisConfig,
    val visited: MutableMap<MethodSignature, Int> = ConcurrentHashMap(),
    val paths: MutableSet<ExecutionPath> = ConcurrentHashMap.newKeySet(),
    val messageBusTopics: MutableSet<PsiClass> = ConcurrentHashMap.newKeySet(),
    val swingComponents: MutableSet<MethodSignature> = ConcurrentHashMap.newKeySet(),
    val statistics: LockReqAnalysisStatistics = LockReqAnalysisStatistics(),
    val inheritorCache: LockReqSearchCache<MethodSignature, PsiMethod> = LockReqSearchCache(),
    val listenerCache: LockReqSearchCache<String, PsiClass> = LockReqSearchCache(),
  )

  context(context: TraversalContext)
  private val config: AnalysisConfig
    get() = context.config

  suspend fun analyzeMethod(method: SmartPsiElementPointer<PsiMethod>, config: AnalysisConfig): AnalysisResult {
    return analyzeMethodStreaming(method, config, method.project, object : LockReqConsumer {})
  }

  data class QueuePayload(val queue: PriorityBlockingQueue<QueueEntry>, val counter: AtomicInteger, val globalCounter: AtomicInteger)

  /**
   * @param statisticsDir the directory for the traversal statistics report.
   * The analysis writes the report when it stops, also on cancellation or failure.
   * If the value is `null`, the analysis does not write the report.
   */
  suspend fun analyzeMethodStreaming(
    methodPointer: SmartPsiElementPointer<PsiMethod>,
    config: AnalysisConfig,
    project: Project,
    consumer: LockReqConsumer,
    statisticsDir: Path? = defaultStatisticsDir(),
  ): AnalysisResult {
    consumer.onStart(methodPointer)
    val context = TraversalContext(config)
    val startNanos = System.nanoTime()
    var status = LockReqAnalysisStatistics.Status.FAILED
    try {
      context(context, consumer, project) {
        traverseMethod(methodPointer)
      }
      status = LockReqAnalysisStatistics.Status.COMPLETED
    }
    catch (e: CancellationException) {
      status = LockReqAnalysisStatistics.Status.CANCELLED
      throw e
    }
    finally {
      if (statisticsDir != null) {
        val wallClockTime = (System.nanoTime() - startNanos).nanoseconds
        val file = withContext(NonCancellable + Dispatchers.IO) {
          writeStatistics(context, statisticsDir, status, wallClockTime)
        }
        if (file != null) {
          consumer.onStatisticsReport(file)
        }
      }
    }

    val result = AnalysisResult(methodPointer, context.paths, context.messageBusTopics, context.swingComponents)
    consumer.onDone(result)
    return result
  }

  private fun writeStatistics(context: TraversalContext, statisticsDir: Path, status: LockReqAnalysisStatistics.Status, wallClockTime: Duration): Path? {
    try {
      val file = context.statistics.writeReport(statisticsDir, context.config, status, wallClockTime)
      LOG.info("Lock analysis statistics are written to $file")
      return file
    }
    catch (e: IOException) {
      LOG.warn("Cannot write lock analysis statistics to $statisticsDir", e)
      return null
    }
  }

  /**
   * @param parentId the id of the traversal node that put this entry into the queue,
   * or [LockReqAnalysisStatistics.NO_PARENT] for the root entry.
   */
  data class QueueEntry(
    val method: SmartPsiElementPointer<PsiMethod>,
    val signature: MethodSignature,
    val path: List<MethodCall>,
    val parentId: Int = LockReqAnalysisStatistics.NO_PARENT,
  ) : Comparable<QueueEntry> {
    override fun compareTo(other: QueueEntry): Int {
      return compareValuesBy(this, other) {
        it.path.size
      }
    }
  }

  context(project: Project, context: TraversalContext, consumer: LockReqConsumer)
  private suspend fun traverseMethod(root: SmartPsiElementPointer<PsiMethod>) {
    val queue = PriorityBlockingQueue<QueueEntry>()
    val totalSize = AtomicInteger(1)
    smartReadAction(project) {
      val method = root.element ?: return@smartReadAction
      val sig = LockReqPsiOps.forLanguage(method.language).extractSignature(method)
      context.statistics.rootSignature = sig
      context.statistics.onEnqueued()
      queue.put(QueueEntry(root, sig, listOf(MethodCall(sig.methodName, sig.containingClassName, method.location()))))
      sig
    }

    val payload = QueuePayload(queue, totalSize, AtomicInteger(1))

    LOG.trace {
      "Starting analysis on ${root.element?.name}"
    }
    context(config, BaseLockReqRules(), project) {
      val isActive = AtomicBoolean(true)
      val time = measureTime {
        reportRawProgress { reporter ->
          val reportingHolder = Collections.synchronizedList<MethodSignature>(ArrayList())
          withContext(Dispatchers.Default) {
            repeat(Runtime.getRuntime().availableProcessors()) {
              launch {
                processIncomingMethods(payload, isActive, reporter, reportingHolder)
              }
            }
          }
        }
      }
      LOG.info("Lock analysis complete in $time")
    }
  }

  context(context: TraversalContext, consumer: LockReqConsumer, rules: LockReqRules, project: Project)
  private suspend fun processIncomingMethods(payload: QueuePayload, isActive: AtomicBoolean, reporter: RawProgressReporter, reportingHolder: MutableList<MethodSignature>) {
    val (queue, totalSize) = payload
    outerLoop@ while (isActive.get()) {
      val peekQueue = try {
        inVirtualThread {
          queue.poll(10, TimeUnit.MILLISECONDS)
        }
      }
      catch (_: NoSuchElementException) {
        break // no more methods to analyze
      }
      if (peekQueue == null) {
        continue
      }
      var statisticsNode: LockReqAnalysisStatistics.Node? = null
      try {
        val (methodPointer, key, currentPath) = peekQueue
        val newValue = currentPath.size
        if (newValue >= config.maxDepth) {
          context.statistics.onSkippedByMaxDepth()
          continue
        }
        while (true) {
          val existing = context.visited.putIfAbsent(key, newValue)
          if (existing == null) {
            break
          }
          if (existing <= newValue) {
            context.statistics.onSkippedAsVisited()
            continue@outerLoop // we have a shorter path already processed
          }
          if (context.visited.replace(key, existing, newValue)) {
            // try replace existing long path with a short path
            break
          }
          // otherwise, someone sneaked their computation; let's try again
        }
        val node = context.statistics.startNode(peekQueue.parentId, key, newValue)
        statisticsNode = node
        val timer = node.timer
        payload.globalCounter.incrementAndGet()
        reporter.fraction(payload.globalCounter.get().toDouble() / (payload.globalCounter.get() + payload.counter.get()))
        reportingHolder.add(key)
        reportProgressCounters(payload, reporter)
        reportCurrentlyProcessedMethod(reportingHolder, reporter)
        LOG.trace {
          "Running analysis on $key (path length: $newValue)"
        }

        timedSmartReadAction(project, timer) {
          val method = timer.measure(Phase.SMART_POINTERS) { methodPointer.element } ?: return@timedSmartReadAction

          val annotationRequirements = timer.measure(Phase.ANNOTATION_CHECK) {
            context(config) {
              LockReqDetector.findAnnotationRequirements(method)
            }
          }
          annotationRequirements.forEach { requirement ->
            val path = ExecutionPath(currentPath, requirement)
            context.paths.add(path)
            consumer.onPath(path)
            LOG.trace {
              "Found requirement: $requirement"
            }
          }
        }

        val callees = timedSmartReadAction(project, timer) {
          val method = timer.measure(Phase.SMART_POINTERS) { methodPointer.element } ?: return@timedSmartReadAction emptyList()
          val ops = LockReqPsiOps.forLanguage(method.language)
          val callees = timer.measure(Phase.TREE_WALK) { ops.getMethodCallees(method, timer) }
          val callSiteLocations = callees.map { it.callSite.location() }
          val pointers = timer.measure(Phase.SMART_POINTERS) { callees.map { SmartPointerManager.createPointer(it.method) } }
          pointers.zip(callSiteLocations)
        }
        node.calleeCount = callees.size

        callees.forEach { (pointer, callSiteLocation) ->
          timedSmartReadAction(project, timer) {
            val callee = timer.measure(Phase.SMART_POINTERS) { pointer.element } ?: return@timedSmartReadAction
            val ops = LockReqPsiOps.forLanguage(callee.language)
            context(ops, config) {
              processCallee(payload, callee, currentPath.withCallSite(callSiteLocation), node.id, timer)
            }
          }
        }
      }
      catch (e: Throwable) {
        LOG.trace {
          "Exception while processing method: ${e.message}"
        }
        throw e
      }
      finally {
        statisticsNode?.let { context.statistics.finishNode(it) }
        val result = totalSize.decrementAndGet()
        reportingHolder.removeIf { it == peekQueue.signature }
        reportCurrentlyProcessedMethod(reportingHolder, reporter)
        if (result == 0) {
          assert(queue.isEmpty())
          LOG.trace {
            buildString {
              appendLine("Global counter: ${payload.globalCounter.get()}")
              context.visited.forEach { signature, i ->
                appendLine("Visited $signature $i times")
              }
            }
          }
          isActive.set(false)
        }
      }
    }

  }

  /**
   * Runs [action] in a smart read action.
   * The wait for the read action counts for [Phase.READ_ACTION_WAIT].
   * The part of [action] that no nested block measures counts for [Phase.OTHER].
   */
  private suspend fun <T> timedSmartReadAction(project: Project, timer: LockReqPhaseTimer, action: () -> T): T {
    return timer.measure(Phase.READ_ACTION_WAIT) {
      smartReadAction(project) {
        timer.measure(Phase.OTHER, action)
      }
    }
  }

  private fun reportProgressCounters(payload: QueuePayload, reporter: RawProgressReporter) {
    reporter.text(DevKitBundle.message("progress.text.processed.0.out.of.1.methods", payload.globalCounter.get(), payload.globalCounter.get() + payload.counter.get()))
  }

  fun reportCurrentlyProcessedMethod(holder: MutableList<MethodSignature>, rawReporter: RawProgressReporter) {
    val presentableName = try {
      holder[0]
    } catch (_: IndexOutOfBoundsException) {
      return
    }
    val qualifiedName = presentableName.containingClassName + "." + presentableName.methodName
    rawReporter.details(DevKitBundle.message("progress.details.analyzing.method.during.lock.requirement.search", qualifiedName))
  }

  /**
   * @param currentPath the chain to the caller of [method]. The last frame points to the call of [method].
   */
  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  context(config: AnalysisConfig, ops: LockReqPsiOps, context: TraversalContext, consumer: LockReqConsumer, rules: LockReqRules)
  private fun processCallee(payload: QueuePayload, method: PsiMethod, currentPath: List<MethodCall>, parentId: Int, timer: LockReqPhaseTimer) {

    val requirements = timer.measure(Phase.CALLEE_CHECK) { LockReqDetector.findBodyRequirements(method) }
    for (requirement in requirements) {
      val path = ExecutionPath(currentPath, requirement)
      context.paths.add(path)
      consumer.onPath(path)
      LOG.trace {
        "Found requirement: $requirement"
      }
    }
    if (requirements.isNotEmpty()) {
      return // this is not interesting further
    }
    if (LockReqDetector.isAsyncDispatch(method)) return
    if (LockReqDetector.isOpaqueMethod(method)) return
    when {
      LockReqDetector.isMessageBusCall(method) -> handleMessageBusCall(payload, method, currentPath, parentId, timer)
      context.config.includePolymorphic && !LockReqDetector.isCommonMethod(method) -> handlePolymorphic(payload, method, currentPath, parentId, timer)
      else -> addToQueueIfNotVisited(payload, method, currentPath, parentId, timer)
    }
  }

  context(ops: LockReqPsiOps, context: TraversalContext, rules: LockReqRules)
  private fun handlePolymorphic(payload: QueuePayload, method: PsiMethod, currentPath: List<MethodCall>, parentId: Int, timer: LockReqPhaseTimer) {
    val signature = MethodSignature.fromMethod(method)
    // the signature of a method in a local or anonymous class does not identify the method
    val cacheKey = signature.takeIf { method.containingClass?.qualifiedName != null }
    val inheritors = context.inheritorCache.getOrSearch(cacheKey, timer, onCacheHit = { context.statistics.onInheritorCacheHit(signature) }) {
      val startNanos = timer.measuredNanos(Phase.INHERITOR_SEARCH)
      val found = ArrayList<PsiMethod>()
      timer.measure(Phase.INHERITOR_SEARCH) {
        ops.findInheritors(method, context.config.scope, context.config.maxImplementations) { found.add(it) }
      }
      context.statistics.onInheritorSearch(signature, timer.measuredNanos(Phase.INHERITOR_SEARCH) - startNanos, found.size)
      found
    }
    for (inheritor in inheritors) {
      addToQueueIfNotVisited(payload, inheritor, currentPath, parentId, timer)
    }
  }

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  context(config: AnalysisConfig, ops: LockReqPsiOps, context: TraversalContext, consumer: LockReqConsumer, lockReqRules: LockReqRules)
  private fun handleMessageBusCall(payload: QueuePayload, method: PsiMethod, currentPath: List<MethodCall>, parentId: Int, timer: LockReqPhaseTimer) {
    ProgressManager.checkCanceled()
    val topicClass = timer.measure(Phase.REFERENCE_RESOLVE) { LockReqDetector.extractMessageBusTopic(method) }
    if (topicClass != null) {
      context.messageBusTopics.add(topicClass)
      consumer.onMessageBusTopic(topicClass)
      val topicName = topicClass.qualifiedName ?: topicClass.name ?: "<anon>"
      val listeners = context.listenerCache.getOrSearch(topicClass.qualifiedName, timer, onCacheHit = { context.statistics.onListenerCacheHit(topicName) }) {
        val startNanos = timer.measuredNanos(Phase.LISTENER_SEARCH)
        val found = ArrayList<PsiClass>()
        timer.measure(Phase.LISTENER_SEARCH) {
          ops.findImplementations(topicClass, context.config.scope, context.config.maxImplementations) { found.add(it) }
        }
        context.statistics.onListenerSearch(topicName, timer.measuredNanos(Phase.LISTENER_SEARCH) - startNanos, found.size)
        found
      }
      for (listener in listeners) {
        listener.methods.forEach { listenerMethod ->
          addToQueueIfNotVisited(payload, listenerMethod, currentPath, parentId, timer)
        }
      }
    }
  }

  context(context: TraversalContext, rules: LockReqRules)
  private fun addToQueueIfNotVisited(payload: QueuePayload, method: PsiMethod, currentPath: List<MethodCall>, parentId: Int, timer: LockReqPhaseTimer) {
    timer.measure(Phase.ENQUEUE) {
      if (context(config) {
          LockReqDetector.shouldBeSkipped(method)
        }) {
        return
      }
      val newPath = currentPath + MethodCall.fromMethod(method)
      payload.counter.incrementAndGet()
      val signature = MethodSignature.fromMethod(method)
      context.statistics.onEnqueued()
      val pointer = timer.measure(Phase.SMART_POINTERS) { SmartPointerManager.createPointer(method) }
      payload.queue.put(QueueEntry(pointer, signature, newPath, parentId))
    }
  }
}