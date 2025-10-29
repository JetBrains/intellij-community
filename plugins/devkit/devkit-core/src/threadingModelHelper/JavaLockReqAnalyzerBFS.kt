// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiClass
import java.util.ArrayDeque
import com.intellij.openapi.progress.ProgressManager
import com.jetbrains.fus.reporting.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger


class JavaLockReqAnalyzerBFS(private val detector: JavaLockReqDetector = JavaLockReqDetector()) : LockReqAnalyzer, LockReqAnalyzerStreaming {

  private data class TraversalContext(
    val config: AnalysisConfig,
    val visited: MutableSet<MethodSignature> = ConcurrentHashMap.newKeySet(),
    val paths: MutableSet<ExecutionPath> = ConcurrentHashMap.newKeySet(),
    val messageBusTopics: MutableSet<PsiClass> = ConcurrentHashMap.newKeySet(),
    val swingComponents: MutableSet<MethodSignature> = ConcurrentHashMap.newKeySet(),
  )

  private val visited = ConcurrentHashMap<String, Unit>()
  private val psiOps = JavaLockReqPsiOps()

  private lateinit var context: TraversalContext
  private var consumer: LockReqConsumer? = null

  override suspend fun analyzeMethod(method: PsiMethod): AnalysisResult {
    return analyzeMethodStreaming(method, object : LockReqConsumer {})
  }

  data class QueuePayload(val queue: Channel<Pair<PsiMethod, List<MethodCall>>>, val counter: AtomicInteger)

  override suspend fun analyzeMethodStreaming(method: PsiMethod, consumer: LockReqConsumer): AnalysisResult {
    val config = AnalysisConfig.forProject(method.project)
    this.consumer = consumer
    consumer.onStart(method)
    context = TraversalContext(config)
    traverseMethod(method)
    val result = AnalysisResult(method, context.paths, context.messageBusTopics, context.swingComponents)
    consumer.onDone(result)
    return result
  }

  private suspend fun traverseMethod(root: PsiMethod) {
    val queue = Channel<Pair<PsiMethod, List<MethodCall>>>(capacity = UNLIMITED)

    visited.clear()
    val totalSize = AtomicInteger(1)
    smartReadAction(root.project) {
      val sig = MethodSignature.fromMethod(root)

      context.visited.add(sig)
      queue.trySend(root to listOf(MethodCall(root)))
      sig
    }

    val payload = QueuePayload(queue, totalSize)

    println("Starting from clean queue!")
    coroutineScope {
      repeat(Runtime.getRuntime().availableProcessors()) {
        launch {
          while (!queue.isClosedForReceive) {
            val peekQueue = try {
              queue.receive()
            } catch (e : NoSuchElementException) {
              break // no more methods to analyze
            }
            try {
              smartReadAction(project = root.project) {
                ProgressManager.checkCanceled()
                val (method, currentPath) = peekQueue
                val annotationRequirements = detector.findAnnotationRequirements(method)
                annotationRequirements.forEach { requirement ->
                  val path = ExecutionPath(currentPath, requirement)
                  context.paths.add(path)
                  consumer?.onPath(path)
                  println("Found requirement: $requirement")
                }

                if (currentPath.size >= context.config.maxDepth) {
                  return@smartReadAction
                }
                psiOps.getMethodCallees(method).forEach { processCallee(payload, it, currentPath) }
              }
            } finally {
              totalSize.decrementAndGet()
              if (totalSize.get() == 0) {
                queue.close()
              }
            }
          }
        }
      }
    }
  }

  private fun processCallee(payload: QueuePayload, method: PsiMethod, currentPath: List<MethodCall>) {
    val containingClassQName = method.containingClass?.qualifiedName
    if (containingClassQName != null) {
      val key = containingClassQName + "." + method.name
      if (visited.containsKey(key) || detector.shouldBeSkipped(method)) {
        return
      }
      visited[key] = Unit
    }
    println("Traversing method ${method.containingClass?.qualifiedName}.${method.name}, ${currentPath.size} steps deep (${currentPath.joinToString(" -> ") { 
      "${it.containingClassName}.${it.methodName}" 
    }})")
    val requirements = detector.findBodyRequirements(method)
    requirements.forEach { requirement ->
      val path = ExecutionPath(currentPath, requirement)
      context.paths.add(path)
      consumer?.onPath(path)
      println("Found requirement: $requirement")
    }
    if (requirements.isNotEmpty()) {
      return // this is not interesting further
    }
    if (detector.isAsyncDispatch(method)) return
    when {
      detector.isMessageBusCall(method) -> handleMessageBusCall(payload, method, currentPath)
      context.config.includePolymorphic && !detector.isCommonMethod(method) -> handlePolymorphic(payload, method, currentPath)
      else -> addToQueueIfNotVisited(payload, method, currentPath)
    }
  }

  private fun handlePolymorphic(payload: QueuePayload, method: PsiMethod, currentPath: List<MethodCall>) {
    val inheritors = ReadAction.nonBlocking(Callable {
      psiOps.findInheritors(method, context.config.scope, context.config.maxImplementations)
    }).executeSynchronously()
    inheritors.forEach { inheritor -> addToQueueIfNotVisited(payload, inheritor, currentPath) }
  }

  private fun handleMessageBusCall(payload: QueuePayload, method: PsiMethod, currentPath: List<MethodCall>) {
    ProgressManager.checkCanceled()
    detector.extractMessageBusTopic(method)?.let { topicClass ->
      context.messageBusTopics.add(topicClass)
      consumer?.onMessageBusTopic(topicClass)
      val listeners = ReadAction.nonBlocking(Callable {
        psiOps.findImplementations(topicClass, context.config.scope, context.config.maxImplementations)
      }).executeSynchronously()
      listeners.forEach { listener ->
        listener.methods.forEach { listenerMethod ->
          addToQueueIfNotVisited(payload, listenerMethod, currentPath)
        }
      }
    }
  }

  private fun addToQueueIfNotVisited(payload: QueuePayload, method: PsiMethod, currentPath: List<MethodCall>) {
    val signature = MethodSignature.fromMethod(method)
    if (signature !in context.visited) {
      context.visited.add(signature)
      val newPath = currentPath + MethodCall(method)
      payload.counter.incrementAndGet()
      payload.queue.trySend(method to newPath)
    }
  }
}