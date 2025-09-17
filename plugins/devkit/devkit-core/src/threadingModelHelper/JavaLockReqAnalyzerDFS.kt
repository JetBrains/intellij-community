// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiClass

class JavaLockReqAnalyzerDFS(private val detector: JavaLockReqDetector = JavaLockReqDetector()) : LockReqAnalyzer {

  private data class TraversalContext(
    val config: AnalysisConfig,
    val currentPath: MutableList<MethodCall> = mutableListOf(),
    val visited: MutableSet<MethodSignature> = mutableSetOf(),
    val paths: MutableSet<ExecutionPath> = mutableSetOf(),
    val messageBusTopics: MutableSet<PsiClass> = mutableSetOf(),
    val swingComponents: MutableSet<MethodSignature> = mutableSetOf(),
  )

  private lateinit var context: TraversalContext
  private val psiOps = JavaLockReqPsiOps()

  override fun analyzeMethod(method: PsiMethod): AnalysisResult {
    val config = AnalysisConfig.forProject(method.project)
    context = TraversalContext(config)
    traverseMethod(method)
    return AnalysisResult(method, context.paths, context.messageBusTopics, context.swingComponents)
  }

  private fun traverseMethod(method: PsiMethod) {
    println("Traversing method ${method.containingClass?.qualifiedName}.${method.name}, ${context.currentPath.size} steps deep")
    val signature = MethodSignature.fromMethod(method)
    if (context.currentPath.size >= context.config.maxDepth || signature in context.visited) return
    context.visited.add(signature)
    context.currentPath.add(MethodCall(method))

    val annotationRequirement = detector.findAnnotationRequirements(method)
    annotationRequirement.forEach { context.paths.add(ExecutionPath(context.currentPath.toList(), it)) }
    psiOps.getMethodCallees(method).forEach { processCallee(it) }

    context.currentPath.removeLast()
  }

  private fun processCallee(callee: PsiMethod) {
    detector.findBodyRequirements(callee).forEach { requirement ->
      context.paths.add(ExecutionPath(context.currentPath.toList(), requirement))
    }
    if (!detector.isAsyncDispatch(callee)) {
      when {
        detector.isMessageBusCall(callee) -> handleMessageBusCall(callee)
        context.config.includePolymorphic -> handlePolymorphic(callee)
        else -> traverseMethod(callee)
      }
    }
  }

  private fun handlePolymorphic(method: PsiMethod) {
    val overrides = psiOps.findInheritors(method, context.config.scope, context.config.maxImplementations)
    overrides.forEach { traverseMethod(it) }
  }

  private fun handleMessageBusCall(method: PsiMethod) {
    val topicClass = detector.extractMessageBusTopic(method) ?: return
    context.messageBusTopics.add(topicClass)
    val listeners = psiOps.findImplementations(topicClass, context.config.scope, context.config.maxImplementations)
    listeners.forEach { it.methods.forEach { method -> traverseMethod(method) } }
  }
}
