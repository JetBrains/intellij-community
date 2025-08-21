// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.psi.*
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ClassInheritorsSearch


class LockReqsAnalyzer(private val detector: LockReqsDetector = LockReqsDetector()) {

  private data class TraversalContext(
    val config: AnalysisConfig,
    val currentPath: MutableList<MethodCall> = mutableListOf(),
    val visited: MutableSet<MethodSignature> = mutableSetOf(),
    val paths: MutableSet<ExecutionPath> = mutableSetOf(),
    val messageBusTopics: MutableSet<String> = mutableSetOf(),
    val swingComponents: MutableSet<MethodSignature> = mutableSetOf(),
  )

  fun analyzeMethod(method: PsiMethod, config: AnalysisConfig = AnalysisConfig.forProject(method.project)): AnalysisResult {
    val context = TraversalContext(config)
    traverseMethod(method, context)
    return AnalysisResult(method, context.paths, context.messageBusTopics, context.swingComponents)
  }

  private fun traverseMethod(method: PsiMethod, context: TraversalContext, isPolymorphic: Boolean = false) {
    if (context.currentPath.size >= context.config.maxDepth) return
    val signature = MethodSignature.fromMethod(method)
    if (signature in context.visited) return
    context.visited.add(signature)
    context.currentPath.add(MethodCall(method, isPolymorphic))

    val annotationRequirements = detector.findAnnotationRequirements(method)
    annotationRequirements.forEach { context.paths.add(ExecutionPath(context.currentPath.toList(), it)) }
    processMethodBody(method, context)
    context.currentPath.removeLast()
  }


  private fun processMethodBody(method: PsiMethod, context: TraversalContext) {
    val body = method.body ?: return
    val localRequirements = mutableListOf<LockRequirement>()

    body.accept(object : JavaRecursiveElementVisitor() {
      override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
        super.visitMethodCallExpression(expression)

        val resolvedMethod = expression.resolveMethod() ?: return
        localRequirements += detector.findBodyRequirements(resolvedMethod)
        if (localRequirements.any { it.requirementReason == RequirementReason.SWING_COMPONENT }) {
          context.swingComponents.add(MethodSignature.fromMethod(resolvedMethod))
        }
        handleMethodCall(resolvedMethod, expression, context)
      }

      override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
        super.visitMethodReferenceExpression(expression)
        (expression.resolve() as? PsiMethod)?.let { resolvedMethod ->
          localRequirements += detector.findBodyRequirements(resolvedMethod)
          if (!detector.isAsyncBoundary(resolvedMethod)) traverseMethod(resolvedMethod, context)
        }
      }

      override fun visitNewExpression(expression: PsiNewExpression) {
        super.visitNewExpression(expression)
        expression.resolveMethod()?.let { resolvedMethod ->
          if (!detector.isAsyncBoundary(resolvedMethod)) traverseMethod(resolvedMethod, context)
        }
      }
    })

    localRequirements.forEach { requirement ->
      val path = ExecutionPath(context.currentPath.toList(), requirement,
                               context.currentPath.any { it.isPolymorphic || it.isMessageBusCall })
      context.paths.add(path)
    }
  }

  private fun handleMethodCall(method: PsiMethod, expression: PsiMethodCallExpression, context: TraversalContext) {
    when {
      context.config.includeMessageBus && detector.isMessageBusCall(expression) -> handleMessageBusCall(method, expression, context)
      context.config.includePolymorphic && detector.isPolymorphicCall(method) -> handlePolymorphicCall(method, context)
      else -> traverseMethod(method, context)
    }
  }

  private fun handleMessageBusCall(method: PsiMethod, expression: PsiMethodCallExpression, context: TraversalContext) {
    detector.extractMessageBusTopic(expression)?.let { context.messageBusTopics.add(it) }
    if (method.name != "syncPublisher") return

    val topicType = method.returnType as? PsiClassType ?: return
    val topicInterface = topicType.resolve() ?: return
    if (!topicInterface.isInterface) return

    findTopicListeners(topicInterface, context.config).forEach { listener ->
      listener.methods.forEach { method -> traverseMethod(method, context) }
    }
  }


  private fun handlePolymorphicCall(method: PsiMethod, context: TraversalContext) {
    val implementations = findImplementations(method, context.config)

    if (implementations.size > context.config.maxImplementations) {
      val requirement = LockRequirement(method, LockType.READ, RequirementReason.IMPLICIT)
      context.currentPath.add(MethodCall(method, isPolymorphic = true))
      context.paths.add(ExecutionPath(context.currentPath.toList(), requirement, true))
      return
    }
    implementations.forEach { traverseMethod(it, context) }
  }

  private fun findImplementations(method: PsiMethod, config: AnalysisConfig): List<PsiMethod> {
    val implementations = mutableListOf<PsiMethod>()
    if (method.body != null) implementations.add(method)

    if (method.hasModifierProperty(PsiModifier.ABSTRACT) || method.containingClass?.isInterface == true) {
      val query = OverridingMethodsSearch.search(method, config.scope, true)
      implementations.addAll(query.findAll().take(config.maxImplementations))
    }
    return implementations
  }


  private fun findTopicListeners(topicInterface: PsiClass, config: AnalysisConfig): List<PsiClass> {
    val query = ClassInheritorsSearch.search(topicInterface, config.scope, true)
    return query.findAll().take(config.maxImplementations).toList()
  }
}