// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.threadingModelHelper

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiClass
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.idea.devkit.threadingModelHelper.AnalysisResult
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqAnalyzer
import kotlin.collections.ArrayDeque
import java.util.concurrent.Callable

class KtLockReqAnalyzerBFS : LockReqAnalyzer {

  private data class TraversalContext(
    val config: KtAnalysisConfig,
    val visited: MutableSet<KtMethodSignature> = mutableSetOf(),
    val paths: MutableSet<KtExecutionPath> = mutableSetOf(),
    val messageBusTopics: MutableSet<PsiClass> = mutableSetOf(),
    val swingComponents: MutableSet<KtMethodSignature> = mutableSetOf(),
  )

  private val queue = ArrayDeque<Pair<PsiMethod, List<KtMethodCall>>>()
  private val psiOps = KtLockReqPsiOps()
  private val detector = KtLockReqDetector()

  private lateinit var context: TraversalContext

  override suspend fun analyzeMethod(method: PsiMethod): AnalysisResult {
    val config = KtAnalysisConfig.forProject(method.project)
    context = TraversalContext(config)
    traverseMethod(method)
    val ktResult = KtAnalysisResult(method, context.paths, context.messageBusTopics, context.swingComponents)
    return ktResult.toCore()
  }

  private fun traverseMethod(root: PsiMethod) {
    queue.clear()
    val startSignature = KtMethodSignature.fromPsi(root)
    context.visited.add(startSignature)
    queue.addLast(root to listOf(KtMethodCall(root)))

    while (queue.isNotEmpty()) {
      ProgressManager.checkCanceled()
      val (method, currentPath) = queue.removeFirst()
      val annotationRequirements = detector.findAnnotationRequirements(method)
      annotationRequirements.forEach { requirement ->
        val path = KtExecutionPath(currentPath, requirement)
        context.paths.add(path)
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
    }

    if (detector.isAsyncDispatch(method)) return
    when {
      detector.isMessageBusCall(method) -> handleMessageBusCall(method, currentPath)
      context.config.includePolymorphic && !detector.isCommonMethod(method) -> handlePolymorphic(method, currentPath)
      else -> addToQueueIfNotVisited(method, currentPath)
    }
  }

  private fun handlePolymorphic(method: PsiMethod, currentPath: List<KtMethodCall>) {
    val inheritors = ReadAction.nonBlocking(Callable {
      psiOps.findInheritors(method, context.config.scope, context.config.maxImplementations)
    }).executeSynchronously()
    inheritors.forEach { inheritor -> addToQueueIfNotVisited(inheritor, currentPath) }
  }

  private fun handleMessageBusCall(method: PsiMethod, currentPath: List<KtMethodCall>) {
    ProgressManager.checkCanceled()
    detector.extractMessageBusTopic(method)?.let { topicClass ->
      context.messageBusTopics.add(topicClass)
      val listeners = ReadAction.nonBlocking(Callable {
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
    val signature = KtMethodSignature.fromPsi(method)
    if (signature !in context.visited) {
      context.visited.add(signature)
      val newPath = currentPath + KtMethodCall(method)
      queue.addLast(method to newPath)
    }
  }
}


