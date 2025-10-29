// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.threadingModelHelper

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.idea.devkit.threadingModelHelper.AnalysisResult
import org.jetbrains.idea.devkit.threadingModelHelper.ExecutionPath
import org.jetbrains.idea.devkit.threadingModelHelper.LockRequirement
import org.jetbrains.idea.devkit.threadingModelHelper.MethodCall
import org.jetbrains.idea.devkit.threadingModelHelper.MethodSignature

data class KtMethodSignature(val qualifiedName: String, val parameterTypes: List<String>) {
  companion object {
    fun fromPsi(method: PsiMethod): KtMethodSignature = KtMethodSignature(
      qualifiedName = "${method.containingClass?.qualifiedName}.${method.name}",
      parameterTypes = method.parameterList.parameters.map { it.type.canonicalText }
    )
  }
  fun toCore(): MethodSignature = MethodSignature(qualifiedName, parameterTypes)
}

data class KtMethodCall(val method: PsiMethod, val isPolymorphic: Boolean = false, val isMessageBusCall: Boolean = false) {
  fun toCore(): MethodCall = MethodCall(method, isPolymorphic = isPolymorphic, isMessageBusCall = isMessageBusCall)
}

data class KtExecutionPath(val methodChain: List<KtMethodCall>, val lockRequirement: LockRequirement, val isSpeculative: Boolean = false) {
  fun toCore(): ExecutionPath = ExecutionPath(methodChain.map { it.toCore() }, lockRequirement, isSpeculative)
}

data class KtAnalysisResult(val method: PsiMethod, val paths: Set<KtExecutionPath>, val messageBusTopics: Set<PsiClass>, val swingComponents: Set<KtMethodSignature>) {
  fun toCore(): AnalysisResult = AnalysisResult(method, paths.map { it.toCore() }.toSet(), messageBusTopics, swingComponents.map { it.toCore() }.toSet())
}

data class KtAnalysisConfig(
  val scope: GlobalSearchScope,
  val maxDepth: Int = 30,
  val maxImplementations: Int = 30,
  val includePolymorphic: Boolean = true
) {
  companion object {
    fun forProject(project: Project): KtAnalysisConfig = KtAnalysisConfig(scope = GlobalSearchScope.projectScope(project))
  }
}