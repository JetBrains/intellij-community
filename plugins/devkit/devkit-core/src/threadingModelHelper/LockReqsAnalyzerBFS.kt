// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiClass
import java.util.ArrayDeque
import com.intellij.openapi.progress.ProgressManager
import java.util.concurrent.Callable


class LockReqsAnalyzerBFS(private val detector: LockReqsDetector = LockReqsDetector()) {

  private data class TraversalContext(
    val config: AnalysisConfig,
    val visited: MutableSet<MethodSignature> = mutableSetOf(),
    val paths: MutableSet<ExecutionPath> = mutableSetOf(),
    val messageBusTopics: MutableSet<PsiClass> = mutableSetOf(),
    val swingComponents: MutableSet<MethodSignature> = mutableSetOf(),
  )

  private val queue = ArrayDeque<Pair<PsiMethod, List<MethodCall>>>()

  private lateinit var context: TraversalContext

  fun analyzeMethod(method: PsiMethod, config: AnalysisConfig = AnalysisConfig.forProject(method.project)): AnalysisResult {
    println("Root: ${method.containingClass?.qualifiedName}.${method.name}")
    context = TraversalContext(config)
    traverseMethod(method)
    return AnalysisResult(method, context.paths, context.messageBusTopics, context.swingComponents)
  }

  private fun traverseMethod(root: PsiMethod) {
    queue.clear()
    val startSignature = MethodSignature.fromMethod(root)
    context.visited.add(startSignature)
    queue.offer(root to listOf(MethodCall(root)))

    while (queue.isNotEmpty()) {
      ProgressManager.checkCanceled()
      val (method, currentPath) = queue.poll()
      val annotationRequirements = detector.findAnnotationRequirements(method)
      annotationRequirements.forEach { requirement ->
        context.paths.add(ExecutionPath(currentPath, requirement))
        println("Found requirement: $requirement")
      }

      if (currentPath.size >= context.config.maxDepth) continue
      LockReqsPsiOps.getMethodCallees(method).forEach { processCallee(it, currentPath) }

    }
  }

  private fun processCallee(method: PsiMethod, currentPath: List<MethodCall>) {
    println("Traversing method ${method.containingClass?.qualifiedName}.${method.name}, ${currentPath.size} steps deep")
    detector.findBodyRequirements(method).forEach { requirement ->
      context.paths.add(ExecutionPath(currentPath, requirement))
      println("Found requirement: $requirement")
    }
    if (detector.isAsyncDispatch(method)) return
    when {
      detector.isMessageBusCall(method) -> handleMessageBusCall(method, currentPath)
      context.config.includePolymorphic && !detector.isCommonMethod(method) -> handlePolymorphic(method, currentPath)
      else -> addToQueueIfNotVisited(method, currentPath)
    }
  }

  private fun handlePolymorphic(method: PsiMethod, currentPath: List<MethodCall>) {
    val inheritors = ReadAction.nonBlocking(Callable {
      LockReqsPsiOps.findInheritors(method, context.config.scope, context.config.maxImplementations)
    }).executeSynchronously()
    inheritors.forEach { inheritor -> addToQueueIfNotVisited(inheritor, currentPath) }
  }

  private fun handleMessageBusCall(method: PsiMethod, currentPath: List<MethodCall>) {
    ProgressManager.checkCanceled()
    detector.extractMessageBusTopic(method)?.let { topicClass ->
      context.messageBusTopics.add(topicClass)
      val listeners = ReadAction.nonBlocking(Callable {
        LockReqsPsiOps.findImplementations(topicClass, context.config.scope, context.config.maxImplementations)
      }).executeSynchronously()
      listeners.forEach { listener ->
        listener.methods.forEach { listenerMethod ->
          addToQueueIfNotVisited(listenerMethod, currentPath)
        }
      }
    }
  }

  private fun addToQueueIfNotVisited(method: PsiMethod, currentPath: List<MethodCall>) {
    val signature = MethodSignature.fromMethod(method)
    if (signature !in context.visited) {
      context.visited.add(signature)
      val newPath = currentPath + MethodCall(method)
      queue.offer(method to newPath)
    }
  }
}