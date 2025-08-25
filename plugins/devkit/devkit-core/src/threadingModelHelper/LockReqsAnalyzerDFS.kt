// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.psi.PsiMethod
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.util.Processor


class LockReqsAnalyzerDFS(private val detector: LockReqsDetector = LockReqsDetector()) {

  private data class TraversalContext(
    val config: AnalysisConfig,
    val currentPath: MutableList<MethodCall> = mutableListOf(),
    val visited: MutableSet<MethodSignature> = mutableSetOf(),
    val paths: MutableSet<ExecutionPath> = mutableSetOf(),
    val messageBusTopics: MutableSet<PsiClass> = mutableSetOf(),
    val swingComponents: MutableSet<MethodSignature> = mutableSetOf(),
  )

  fun analyzeMethod(method: PsiMethod, config: AnalysisConfig = AnalysisConfig.forProject(method.project)): AnalysisResult {
    val context = TraversalContext(config)
    traverseMethod(method, context)
    return AnalysisResult(method, context.paths, context.messageBusTopics, context.swingComponents)
  }

  private fun traverseMethod(method: PsiMethod, context: TraversalContext) {
    println("Traversing ${method.containingClass?.qualifiedName}.${method.name}")
    val signature = MethodSignature.fromMethod(method)
    if (context.currentPath.size >= context.config.maxDepth || signature in context.visited) return
    context.visited.add(signature)
    context.currentPath.add(MethodCall(method))

    val annotationRequirement = detector.findAnnotationRequirements(method)
    annotationRequirement.forEach { context.paths.add(ExecutionPath(context.currentPath.toList(), it)) }
    getMethodCallees(method).forEach { processCallee(it, context) }

    context.currentPath.removeLast()
  }

  private fun getMethodCallees(method: PsiMethod): List<PsiMethod> {
    val callees = mutableListOf<PsiMethod>()

    method.body?.accept(object : JavaRecursiveElementVisitor() {
      override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
        super.visitMethodCallExpression(expression)
        expression.resolveMethod()?.let { callees.add(it) }
      }

      override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
        super.visitMethodReferenceExpression(expression)
        (expression.resolve() as? PsiMethod)?.let { callees.add(it) }
      }

      override fun visitNewExpression(expression: PsiNewExpression) {
        super.visitNewExpression(expression)
        expression.resolveMethod()?.let { callees.add(it) }
      }
    })

    return callees
  }

  private fun processCallee(callee: PsiMethod, context: TraversalContext) {
    detector.findBodyRequirements(callee).forEach { requirement ->
      context.paths.add(ExecutionPath(context.currentPath.toList(), requirement))
    }
    if (!detector.isAsyncDispatch(callee)) {
      when {
        detector.isMessageBusCall(callee) -> handleMessageBusCall(callee, context)
        detector.isPolymorphicCall(callee) -> handlePolymorphic(callee, context)
        else -> traverseMethod(callee, context)
      }
    }
  }

  private fun handlePolymorphic(method: PsiMethod, context: TraversalContext) {
    val implementations = findImplementations(method, context.config)
    implementations.forEach { traverseMethod(it, context) }
  }

  private fun findImplementations(method: PsiMethod, config: AnalysisConfig): List<PsiMethod> {
    val implementations = mutableListOf<PsiMethod>()
    if (method.body != null) {
      implementations.add(method)
    }
    if (method.hasModifierProperty(PsiModifier.ABSTRACT) || method.containingClass?.isInterface == true) {
      val query = OverridingMethodsSearch.search(method, config.scope, true)
      query.forEach(Processor<PsiMethod> {
        if (implementations.size >= config.maxImplementations) return@Processor false
        implementations.add(it)
      })
    }
    return implementations
  }

  private fun handleMessageBusCall(method: PsiMethod, context: TraversalContext) {
    detector.extractMessageBusTopic(method)?.let { context.messageBusTopics.add(it) }

    val topicType = method.returnType as? PsiClassType ?: return
    val topicInterface = topicType.resolve() ?: return
    if (!topicInterface.isInterface) return

    detector.findTopicListeners(topicInterface, context.config).forEach { listener ->
      listener.methods.forEach { method -> traverseMethod(method, context) }
    }
  }
}
