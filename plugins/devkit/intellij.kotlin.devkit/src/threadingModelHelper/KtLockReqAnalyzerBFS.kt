// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.threadingModelHelper

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiClass
import org.jetbrains.idea.devkit.threadingModelHelper.AnalysisConfig
import org.jetbrains.idea.devkit.threadingModelHelper.AnalysisResult
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqAnalyzer
import org.jetbrains.idea.devkit.threadingModelHelper.MethodSignature
import org.jetbrains.idea.devkit.threadingModelHelper.MethodCall
import org.jetbrains.idea.devkit.threadingModelHelper.ExecutionPath
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

class KtLockReqAnalyzerBFS : LockReqAnalyzer, org.jetbrains.idea.devkit.threadingModelHelper.LockReqAnalyzerStreaming {

  private data class TraversalContext(
    val config: AnalysisConfig,
    val visited: MutableSet<MethodSignature> = mutableSetOf(),
    val paths: MutableSet<KtExecutionPath> = mutableSetOf(),
    val messageBusTopics: MutableSet<PsiClass> = mutableSetOf(),
    val swingComponents: MutableSet<KtMethodSignature> = mutableSetOf(),
  )

  private val queue = java.util.ArrayDeque<Pair<PsiMethod, List<KtMethodCall>>>()
  private val psiOps = KtLockReqPsiOps()
  private val detector = KtLockReqDetector()

  private lateinit var context: TraversalContext
  private var consumer: org.jetbrains.idea.devkit.threadingModelHelper.LockReqConsumer? = null

  override fun analyzeMethod(function: PsiMethod): AnalysisResult {
    return analyzeMethodStreaming(function, object : org.jetbrains.idea.devkit.threadingModelHelper.LockReqConsumer {})
  }

  override fun analyzeMethodStreaming(function: PsiMethod, consumer: org.jetbrains.idea.devkit.threadingModelHelper.LockReqConsumer): AnalysisResult {
    val config = AnalysisConfig.forProject(function.project)
    this.consumer = consumer
    consumer.onStart(function)
    context = TraversalContext(config)
    traverseMethod(function)
    val ktResult = KtAnalysisResult(function, context.paths, context.messageBusTopics, context.swingComponents)
    val result = ktResult.toCore()
    consumer.onDone(result)
    return result
  }

  private fun traverseMethod(root: PsiMethod) {
    queue.clear()
    val startSignature = MethodSignature.fromMethod(root)
    context.visited.add(startSignature)
    queue.offer(root to listOf(KtMethodCall(root)))

    while (queue.isNotEmpty()) {
      com.intellij.openapi.progress.ProgressManager.checkCanceled()
      val (method, currentPath) = queue.poll()
      val annotationRequirements = detector.findAnnotationRequirements(method)
      annotationRequirements.forEach { requirement ->
        val path = KtExecutionPath(currentPath, requirement)
        context.paths.add(path)
        consumer?.onPath(path.toCore())
      }

      if (currentPath.size >= context.config.maxDepth) continue
      psiOps.getMethodCallees(method).forEach { processCallee(it, currentPath) }
    }
  }

  private fun processCallee(method: PsiMethod, currentPath: List<KtMethodCall>) {
    val bodyReqs = detector.findBodyRequirements(method)
    bodyReqs.forEach { requirement ->
      val path = KtExecutionPath(currentPath, requirement)
      context.paths.add(path)
      consumer?.onPath(path.toCore())
    }

    if (detector.isAsyncDispatch(method)) return
    when {
      detector.isMessageBusCall(method) -> handleMessageBusCall(method, currentPath)
      context.config.includePolymorphic && !detector.isCommonMethod(method) -> handlePolymorphic(method, currentPath)
      else -> addToQueueIfNotVisited(method, currentPath)
    }
  }

  private fun handlePolymorphic(method: PsiMethod, currentPath: List<KtMethodCall>) {
    val inheritors = com.intellij.openapi.application.ReadAction.nonBlocking(java.util.concurrent.Callable {
      psiOps.findInheritors(method, context.config.scope, context.config.maxImplementations)
    }).executeSynchronously()
    inheritors.forEach { inheritor -> addToQueueIfNotVisited(inheritor, currentPath) }
  }

  private fun handleMessageBusCall(method: PsiMethod, currentPath: List<KtMethodCall>) {
    com.intellij.openapi.progress.ProgressManager.checkCanceled()
    detector.extractMessageBusTopic(method)?.let { topicClass ->
      context.messageBusTopics.add(topicClass)
      consumer?.onMessageBusTopic(topicClass)
      val listeners = com.intellij.openapi.application.ReadAction.nonBlocking(java.util.concurrent.Callable {
        psiOps.findImplementations(topicClass, context.config.scope, context.config.maxImplementations)
      }).executeSynchronously()
      listeners.forEach { listener ->
        listener.methods.forEach { listenerMethod ->
          addToQueueIfNotVisited(listenerMethod, currentPath)
        }
      }
    }
  }

  private fun addToQueueIfNotVisited(method: PsiMethod, currentPath: List<KtMethodCall>) {
    val signature = MethodSignature.fromMethod(method)
    if (signature !in context.visited) {
      context.visited.add(signature)
      val newPath = currentPath + KtMethodCall(method)
      queue.offer(method to newPath)
    }
  }
}


