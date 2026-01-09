// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.concurrency.virtualThreads.inVirtualThread
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.DevKitBundle
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
  }

  private data class TraversalContext(
    val config: AnalysisConfig,
    val visited: MutableMap<MethodSignature, Int> = ConcurrentHashMap(),
    val paths: MutableSet<ExecutionPath> = ConcurrentHashMap.newKeySet(),
    val messageBusTopics: MutableSet<PsiClass> = ConcurrentHashMap.newKeySet(),
    val swingComponents: MutableSet<MethodSignature> = ConcurrentHashMap.newKeySet(),
  )

  context(context: TraversalContext)
  private val config: AnalysisConfig
    get() = context.config

  suspend fun analyzeMethod(method: SmartPsiElementPointer<PsiMethod>, config: AnalysisConfig): AnalysisResult {
    return analyzeMethodStreaming(method, config, method.project, object : LockReqConsumer {})
  }

  data class QueuePayload(val queue: PriorityBlockingQueue<QueueEntry>, val counter: AtomicInteger, val globalCounter: AtomicInteger)

  suspend fun analyzeMethodStreaming(methodPointer: SmartPsiElementPointer<PsiMethod>, config: AnalysisConfig, project: Project, consumer: LockReqConsumer): AnalysisResult {
    consumer.onStart(methodPointer)
    val context = TraversalContext(config)
    context(context, consumer, project) {
      traverseMethod(methodPointer)
    }

    val result = AnalysisResult(methodPointer, context.paths, context.messageBusTopics, context.swingComponents)
    consumer.onDone(result)
    return result
  }

  data class QueueEntry(val method: SmartPsiElementPointer<PsiMethod>, val signature: MethodSignature, val path: List<MethodCall>) : Comparable<QueueEntry> {
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
      try {
        val (methodPointer, key, currentPath) = peekQueue
        val newValue = currentPath.size
        if (newValue >= config.maxDepth) {
          continue
        }
        while (true) {
          val existing = context.visited.putIfAbsent(key, newValue)
          if (existing == null) {
            break
          }
          if (existing <= newValue) {
            continue@outerLoop // we have a shorter path already processed
          }
          if (context.visited.replace(key, existing, newValue)) {
            // try replace existing long path with a short path
            break
          }
          // otherwise, someone sneaked their computation; let's try again
        }
        payload.globalCounter.incrementAndGet()
        reporter.fraction(payload.globalCounter.get().toDouble() / (payload.globalCounter.get() + payload.counter.get()))
        reportingHolder.add(key)
        reportProgressCounters(payload, reporter)
        reportCurrentlyProcessedMethod(reportingHolder, reporter)
        LOG.trace {
          "Running analysis on $key (path length: $newValue)"
        }

        smartReadAction(project = project) {
          val method = methodPointer.element ?: return@smartReadAction

          val annotationRequirements = context(config) {
            LockReqDetector.findAnnotationRequirements(method)
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

        val callees = smartReadAction(project) {
          val method = methodPointer.element ?: return@smartReadAction emptyList()
          val ops = LockReqPsiOps.forLanguage(method.language)
          ops.getMethodCallees(method).map { SmartPointerManager.createPointer(it) }
        }

        callees.forEach {
          smartReadAction(project) {
            val callee = it.element ?: return@smartReadAction
            val ops = LockReqPsiOps.forLanguage(callee.language)
            context(ops, config) {
              processCallee(payload, callee, currentPath)
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
        val result = totalSize.decrementAndGet()
        reportingHolder.removeIf { it == peekQueue.signature }
        reportCurrentlyProcessedMethod(reportingHolder, reporter)
        if (result == 0) {
          assert(queue.isEmpty())
          LOG.trace {
            buildString {
              "Global counter: ${payload.globalCounter.get()}"
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

  @RequiresReadLock
  context(config: AnalysisConfig, ops: LockReqPsiOps, context: TraversalContext, consumer: LockReqConsumer, rules: LockReqRules)
  private fun processCallee(payload: QueuePayload, method: PsiMethod, currentPath: List<MethodCall>) {

    val requirements = LockReqDetector.findBodyRequirements(method)
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
    when {
      LockReqDetector.isMessageBusCall(method) -> handleMessageBusCall(payload, method, currentPath)
      context.config.includePolymorphic && !LockReqDetector.isCommonMethod(method) -> handlePolymorphic(payload, method, currentPath)
      else -> addToQueueIfNotVisited(payload, method, currentPath)
    }
  }

  context(ops: LockReqPsiOps, context: TraversalContext, rules: LockReqRules)
  private fun handlePolymorphic(payload: QueuePayload, method: PsiMethod, currentPath: List<MethodCall>) {
    ops.findInheritors(method, context.config.scope, context.config.maxImplementations) { inheritor ->
      addToQueueIfNotVisited(payload, inheritor, currentPath)
    }
  }

  @RequiresReadLock
  context(config: AnalysisConfig, ops: LockReqPsiOps, context: TraversalContext, consumer: LockReqConsumer, lockReqRules: LockReqRules)
  private fun handleMessageBusCall(payload: QueuePayload, method: PsiMethod, currentPath: List<MethodCall>) {
    ProgressManager.checkCanceled()
    val topicClass = LockReqDetector.extractMessageBusTopic(method)
    if (topicClass != null) {
      context.messageBusTopics.add(topicClass)
      consumer.onMessageBusTopic(topicClass)
      ops.findImplementations(topicClass, context.config.scope, context.config.maxImplementations) { listener ->
        listener.methods.forEach { listenerMethod ->
          addToQueueIfNotVisited(payload, listenerMethod, currentPath)
        }
      }
    }
  }

  context(context: TraversalContext, rules: LockReqRules)
  private fun addToQueueIfNotVisited(payload: QueuePayload, method: PsiMethod, currentPath: List<MethodCall>) {
    if (context(config) {
        LockReqDetector.shouldBeSkipped(method)
      }) {
      return
    }
    val newPath = currentPath + MethodCall.fromMethod(method)
    payload.counter.incrementAndGet()
    val signature = MethodSignature.fromMethod(method)
    payload.queue.put(QueueEntry(SmartPointerManager.createPointer(method), signature, newPath))
  }
}