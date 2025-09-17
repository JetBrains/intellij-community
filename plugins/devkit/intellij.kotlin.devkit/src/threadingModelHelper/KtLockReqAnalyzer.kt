// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.threadingModelHelper

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiMethod
import org.jetbrains.idea.devkit.threadingModelHelper.AnalysisConfig
import org.jetbrains.idea.devkit.threadingModelHelper.AnalysisResult
import org.jetbrains.idea.devkit.threadingModelHelper.LockReqAnalyzer
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

class KtLockReqAnalyzer(private val detector: KtLockReqDetector = KtLockReqDetector()) : LockReqAnalyzer {

  private lateinit var config: AnalysisConfig

  override fun analyzeMethod(function: PsiMethod): AnalysisResult {
    TODO()
  }

  private fun analyzeElement(element: KtElement, currentPath: List<KtMethodCall>, context: AnalysisContext, ) {
    ProgressManager.checkCanceled()
    val signature = createSignature(element) ?: return
    if (signature in context.visited) return
    if (currentPath.size >= context.config.maxDepth) return
    context.visited.add(signature)
    val calls = findCalls(element)
    for (call in calls) {
      processCall(call, currentPath, context)
    }
  }

  private fun processCall(call: KtCallExpression, currentPath: List<KtMethodCall>, context: AnalysisContext, ) {
    if (detector.isCoroutineDispatch(call)) return
    TODO()
  }

  private fun handlePolymorphicCall(call: KtCallExpression, currentPath: List<KtMethodCall>, context: AnalysisContext, ) {
    TODO()
  }

  private fun handleArgumentsAndLambdas(call: KtCallExpression, currentPath: List<KtMethodCall>, context: AnalysisContext, ) {
    call.valueArguments.forEach { arg ->
      val argExpr = arg.getArgumentExpression() ?: return@forEach
      when (argExpr) {
        is KtLambdaExpression -> {
          val body = argExpr.bodyExpression ?: return@forEach
          analyzeElement(body, currentPath, context)
        }
        is KtFunction -> analyzeElement(argExpr, currentPath, context)
        is KtCallExpression -> processCall(argExpr, currentPath, context)
        is KtElement -> analyzeElement(argExpr, currentPath, context)
      }
    }
  }

  private fun findCalls(element: KtElement): List<KtCallExpression> {
    return element.collectDescendantsOfType<KtCallExpression>()
  }

  private fun createSignature(element: KtElement): String? {
    return when (element) {
      is KtFunction -> {
        val name = element.fqName?.asString() ?: element.name
        val params = element.valueParameters.joinToString(",", "(", ")") { it.typeReference?.text ?: "?" }
        listOfNotNull(name).joinToString("") + params
      }
      is KtLambdaExpression -> "lambda@" + element.textRange?.startOffset
      else -> element.textRange?.let { "el@" + it.startOffset + ":" + it.endOffset }
    }
  }

  private data class AnalysisContext(
    val config: AnalysisConfig,
    val visited: MutableSet<String> = mutableSetOf(),
    val paths: MutableSet<KtExecutionPath> = mutableSetOf(),
  )
}


