// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiClass
import java.util.ArrayDeque

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
      val (method, currentPath) = queue.poll()
      val annotationRequirements = detector.findAnnotationRequirements(method)
      annotationRequirements.forEach { requirement ->
        context.paths.add(ExecutionPath(currentPath, requirement))
      }

      if (currentPath.size >= context.config.maxDepth) continue
      LockReqsPsiOps.getMethodCallees(method).forEach { processCallee(it, currentPath) }
    }
  }

  private fun processCallee(callee: PsiMethod, currentPath: List<MethodCall>) {
    detector.findBodyRequirements(callee).forEach { requirement ->
      context.paths.add(ExecutionPath(currentPath, requirement))
    }
    if (detector.isAsyncDispatch(callee)) return
    when {
      detector.isMessageBusCall(callee) -> handleMessageBusCall(callee, currentPath)
      LockReqsPsiOps.canBeOverridden(callee) -> handlePolymorphic(callee, currentPath)
      else -> addToQueueIfNotVisited(callee, currentPath)
    }
  }

  private fun handlePolymorphic(method: PsiMethod, currentPath: List<MethodCall>) {
    val inheritors = LockReqsPsiOps.findInheritors(method, context.config.scope, context.config.maxImplementations)
    inheritors.forEach { inheritor -> addToQueueIfNotVisited(inheritor, currentPath) }
  }

  private fun handleMessageBusCall(method: PsiMethod, currentPath: List<MethodCall>) {
    detector.extractMessageBusTopic(method)?.let { topicClass ->
      context.messageBusTopics.add(topicClass)
      val listeners = LockReqsPsiOps.findImplementations(topicClass, context.config.scope, context.config.maxImplementations)
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