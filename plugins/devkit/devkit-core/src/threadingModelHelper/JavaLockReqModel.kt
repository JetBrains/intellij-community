// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope

enum class LockType { READ, WRITE, WRITE_INTENT, EDT, BGT, NO_READ }

enum class RequirementReason { ANNOTATION, ASSERTION, SWING_COMPONENT, MESSAGE_BUS, IMPLICIT }

data class LockRequirement(val source: PsiElement, val lockType: LockType, val requirementReason: RequirementReason)

data class MethodSignature(val qualifiedName: String, val parameterTypes: List<String>) {
  companion object {
    fun fromMethod(method: PsiMethod): MethodSignature = MethodSignature(
      qualifiedName = "${method.containingClass?.qualifiedName}.${method.name}",
      parameterTypes = method.parameterList.parameters.map { it.type.canonicalText }
    )
  }
}

data class MethodCall(val method: PsiMethod, val isPolymorphic: Boolean = false, val isMessageBusCall: Boolean = false)

data class ExecutionPath(val methodChain: List<MethodCall>, val lockRequirement: LockRequirement, val isSpeculative: Boolean = false)

data class AnalysisResult(val method: PsiMethod, val paths: Set<ExecutionPath>, val messageBusTopics: Set<PsiClass>, val swingComponents: Set<MethodSignature>)

data class AnalysisConfig(val scope: GlobalSearchScope, val maxDepth: Int = 30, val maxImplementations: Int = 30, val includePolymorphic: Boolean = true) {
  companion object {
    fun forProject(project: Project): AnalysisConfig = AnalysisConfig(scope = GlobalSearchScope.projectScope(project))
  }
}


