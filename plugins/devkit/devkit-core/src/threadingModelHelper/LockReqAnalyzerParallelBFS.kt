// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.measureTime

/**
 * The main controller of call graph traversal
 * The analysis is performed in parallel
 */
class LockReqAnalyzerParallelBFS {

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

  suspend fun analyzeMethod(method: PsiMethod, config: AnalysisConfig): AnalysisResult {
    return analyzeMethodStreaming(method, config, object : LockReqConsumer {})
  }

  data class QueuePayload(val queue: Channel<Pair<PsiMethod, List<MethodCall>>>, val counter: AtomicInteger, val globalCounter: AtomicInteger)

  suspend fun analyzeMethodStreaming(method: PsiMethod, config: AnalysisConfig, consumer: LockReqConsumer): AnalysisResult {
    consumer.onStart(method)
    val context = TraversalContext(config)
    context(context, consumer) {
      traverseMethod(method)
    }

    val result = AnalysisResult(method, context.paths, context.messageBusTopics, context.swingComponents)
    consumer.onDone(result)
    return result
  }

  context(context: TraversalContext, consumer: LockReqConsumer)
  private suspend fun traverseMethod(root: PsiMethod) {
    val queue = Channel<Pair<PsiMethod, List<MethodCall>>>(UNLIMITED)
    val totalSize = AtomicInteger(1)
    smartReadAction(root.project) {
      val sig = MethodSignature.fromMethod(root)
      queue.trySend(root to listOf(MethodCall(root)))
      sig
    }

    val payload = QueuePayload(queue, totalSize, AtomicInteger(1))

    println("Starting from clean queue!")
    context(config, BaseLockReqRules(), root.project) {
      val time = measureTime {
        withContext(Dispatchers.Default) {
          repeat(Runtime.getRuntime().availableProcessors()) {
            launch {
              processIncomingMethods(payload, root)
            }
          }
        }
      }
      println("Lock analysis complete in $time")
    }
  }

  context(context: TraversalContext, consumer: LockReqConsumer, rules: LockReqRules, project: Project)
  private suspend fun processIncomingMethods(payload: QueuePayload, root: PsiMethod) {
    val (queue, totalSize) = payload
    while (true) {
      val peekQueue = try {
        queue.receive()
      }
      catch (_: NoSuchElementException) {
        break // no more methods to analyze
      }
      try {
        smartReadAction(project = project) {
          val (method, currentPath) = peekQueue

          val time = measureTime {

            val key = MethodSignature.fromMethod(method)
            val newValue = currentPath.size
            while (true) {
              val existing = context.visited.putIfAbsent(key, newValue)
              if (existing == null) {
                break
              }
              if (existing <= newValue) {
                return@smartReadAction // we have a shorter path already processed
              }
              if (context.visited.replace(key, existing, newValue)) {
                // try replace existing long path with a short path
                break
              }
              // otherwise, someone sneaked their computation; let's try again
            }

            payload.globalCounter.incrementAndGet()
            val annotationRequirements = context(config) {
              LockReqDetector.findAnnotationRequirements(method)
            }
            annotationRequirements.forEach { requirement ->
              val path = ExecutionPath(currentPath, requirement)
              context.paths.add(path)
              consumer.onPath(path)
              println("Found requirement: $requirement")
            }

            if (currentPath.size >= context.config.maxDepth) {
              return@smartReadAction
            }
            val ops = LockReqPsiOps.forLanguage(method.language)
            ops.getMethodCallees(method).forEach {
              context(ops, config) {
                processCallee(payload, it, currentPath)
              }
            }
          }

          println("Traversed method ${method.containingClass?.qualifiedName}.${method.name} in $time, ${currentPath.size} steps deep (${
            currentPath.joinToString(" -> ") {
              "${it.containingClassName}.${it.methodName}"
            }
          })")
        }
      }
      catch (e: Throwable) {
        println("Exception while processing method: ${e.message}")
        throw e
      }
      finally {
        val result = totalSize.decrementAndGet()
        if (result == 0) {
          assert(queue.isEmpty)
          println("Global counter: ${payload.globalCounter.get()}")
          queue.close()
        }
      }
    }

  }

  context(config: AnalysisConfig, ops: LockReqPsiOps, context: TraversalContext, consumer: LockReqConsumer, rules: LockReqRules)
  private fun processCallee(payload: QueuePayload, method: PsiMethod, currentPath: List<MethodCall>) {

    val requirements = LockReqDetector.findBodyRequirements(method)
    requirements.forEach { requirement ->
      val path = ExecutionPath(currentPath, requirement)
      context.paths.add(path)
      consumer.onPath(path)
      println("Found requirement: $requirement")
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

  context(config: AnalysisConfig, ops: LockReqPsiOps, context: TraversalContext, consumer: LockReqConsumer, lockReqRules: LockReqRules)
  private fun handleMessageBusCall(payload: QueuePayload, method: PsiMethod, currentPath: List<MethodCall>) {
    ProgressManager.checkCanceled()
    LockReqDetector.extractMessageBusTopic(method)?.let { topicClass ->
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
    val newPath = currentPath + MethodCall(method)
    payload.counter.incrementAndGet()
    assert(payload.queue.trySend(method to newPath).isSuccess)
  }
}