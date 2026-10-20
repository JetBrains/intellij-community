// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import java.util.EnumSet

enum class ConstraintType { READ, WRITE, WRITE_INTENT, EDT, BGT, NO_READ }

val LOCK_REQUIREMENTS: EnumSet<ConstraintType> = EnumSet.of(ConstraintType.READ, ConstraintType.WRITE, ConstraintType.WRITE_INTENT, ConstraintType.NO_READ)

val THREAD_REQUIREMENTS: EnumSet<ConstraintType> = EnumSet.of(ConstraintType.EDT, ConstraintType.BGT)

enum class RequirementReason { ANNOTATION, ASSERTION, SWING_COMPONENT, MESSAGE_BUS, IMPLICIT }

data class LockRequirement(val source: PsiElement, val constraintType: ConstraintType, val requirementReason: RequirementReason)

data class MethodSignature(val containingClassName: String, val methodName: String, val parameterTypes: List<String>) {
  companion object {
    fun fromMethod(method: PsiMethod): MethodSignature = MethodSignature(
      containingClassName = method.containingClass?.qualifiedName ?: "<anon>",
      methodName = method.name,
      parameterTypes = method.parameterList.parameters.map { it.type.canonicalText }
    )
  }
}

data class MethodCall(
  val methodName: String,
  val containingClassName: String?,
  /**
   * Navigatable coordinates of this call in the stack trace format `File.java:line`.
   * The line number is 1-based.
   * In an [ExecutionPath], the line is the call of the next frame, or the access that causes the [LockRequirement].
   * If the analysis did not go into the method body, the line is the method declaration.
   */
  val sourceLocation: String,
  val isPolymorphic: Boolean = false,
  val isMessageBusCall: Boolean = false,
) {
  companion object {
    fun fromMethod(method: PsiMethod): MethodCall {
      return MethodCall(method.name, method.containingClass?.qualifiedName ?: "<anon>", method.location())
    }
  }
}

internal fun PsiElement.location(): String {
  val containingDocument = containingFile.fileDocument
  val containingFile = containingFile.virtualFile?.name ?: "<unknown>"
  val elementLocation = textOffset.takeIf { it >= 0 }?.let { containingDocument.getLineNumber(it) + 1 } ?: -1
  return "$containingFile:$elementLocation"
}

/**
 * The chain with the line of the last frame moved to [callSiteLocation].
 */
internal fun List<MethodCall>.withCallSite(callSiteLocation: String): List<MethodCall> {
  return dropLast(1) + last().copy(sourceLocation = callSiteLocation)
}

data class ExecutionPath(val methodChain: List<MethodCall>, val lockRequirement: LockRequirement, val isSpeculative: Boolean = false)

data class AnalysisResult(val method: SmartPsiElementPointer<PsiMethod>, val paths: Set<ExecutionPath>, val messageBusTopics: Set<PsiClass>, val swingComponents: Set<MethodSignature>)

/**
 * Input parameters for the search session
 */
data class AnalysisConfig(
  val scope: GlobalSearchScope,
  /**
   * The set of constraints that will be searched for in the codebase
   */
  val interestingConstraintTypes: EnumSet<ConstraintType>,
  val maxDepth: Int = 5,
  val maxImplementations: Int = 5,
  val includePolymorphic: Boolean = true,
) {
  companion object {
    fun forProject(project: Project, interestingConstraintTypes: EnumSet<ConstraintType>): AnalysisConfig = AnalysisConfig(scope = GlobalSearchScope.projectScope(project), interestingConstraintTypes = interestingConstraintTypes)
  }
}


