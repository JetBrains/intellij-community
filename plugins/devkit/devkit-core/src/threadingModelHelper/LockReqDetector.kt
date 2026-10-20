// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.InheritanceUtil

object LockReqDetector {

  context(config: AnalysisConfig, patterns: LockReqRules)
  fun findAnnotationRequirements(method: PsiMethod): List<LockRequirement> {
    val requirements = mutableListOf<LockRequirement>()
    method.annotations.forEach { annotation ->
      patterns.lockAnnotations[annotation.qualifiedName]?.let { lockType ->
        if (lockType in config.interestingConstraintTypes) {
          requirements.add(LockRequirement(method, lockType, RequirementReason.ANNOTATION))
        }
      }
    }
    return requirements
  }

  context(config: AnalysisConfig, patterns: LockReqRules)
  fun findBodyRequirements(method: PsiMethod): List<LockRequirement> {
    val requirements = mutableListOf<LockRequirement>()
    val className = method.containingClass?.qualifiedName
    val methodName = method.name

    patterns.assertionMethods[className]?.get(methodName)?.let { lockType ->
      if (lockType in config.interestingConstraintTypes) {
        requirements.add(LockRequirement(method, lockType, RequirementReason.ASSERTION))
      }
    }

    if (ConstraintType.READ in config.interestingConstraintTypes && className in patterns.readRequiredClasses) {
      requirements.add(LockRequirement(method, ConstraintType.READ, RequirementReason.IMPLICIT))
    }

    if (config.interestingConstraintTypes.contains(ConstraintType.EDT) && isSwingMethod(method)) {
      requirements.add(LockRequirement(method, ConstraintType.EDT, RequirementReason.SWING_COMPONENT))
    }
    return requirements
  }

  context(config: AnalysisConfig, patterns: LockReqRules)
  private fun isSwingMethod(method: PsiMethod): Boolean {
    val containingClass = method.containingClass ?: return false
    val className = containingClass.qualifiedName ?: return false
    if (isSwingClass(className)) return method.name !in patterns.safeSwingMethods
    return psiOps(method).inheritsFromAny(containingClass, patterns.edtRequiredClasses)
  }

  context(config: AnalysisConfig, patterns: LockReqRules)
  private fun isSwingClass(className: String): Boolean {
    return patterns.edtRequiredClasses.contains(className) ||
           patterns.edtRequiredPackages.any { prefix -> className.startsWith("$prefix.") }
  }

  fun psiOps(method: PsiMethod): LockReqPsiOps {
    return LockReqPsiOps.forLanguage(method.language)
  }

  context(config: AnalysisConfig, patterns: LockReqRules)
  fun isAsyncDispatch(method: PsiMethod): Boolean {
    return patterns.asyncMethods.any { (className, methodName) ->
      method.containingClass?.qualifiedName == className && method.name == methodName }
  }


  context(config: AnalysisConfig, patterns: LockReqRules)
  fun isMessageBusCall(method: PsiMethod): Boolean {
    val containingClass = method.containingClass?.qualifiedName ?: return false
    return patterns.messageBusClasses.contains(containingClass) &&
           method.name in patterns.messageBusSyncMethods
  }

  context(config: AnalysisConfig, patterns: LockReqRules)
  fun extractMessageBusTopic(method: PsiMethod): PsiClass? {
    if (method.name in patterns.messageBusSyncMethods) {
      return psiOps(method).resolveReturnType(method)
    }
    return null
  }

  /**
   * Whether this method guaranteedly does not contain interesting traces
   */
  context(config: AnalysisConfig, patterns: LockReqRules)
  fun shouldBeSkipped(method: PsiMethod): Boolean {
    val name = method.name
    val containingFqn = method.containingClass?.qualifiedName ?: ""
    return patterns.indifferent.any { (scopeOfIndifference, sig) ->
      scopeOfIndifference.containsAll(config.interestingConstraintTypes)
      && sig.methodName == name
      && containingFqn == sig.classFqn
    }
           || containingFqn.startsWith("java")
           || containingFqn.startsWith("com.sun")
           || containingFqn.startsWith("com.intellij.util")
           || containingFqn.startsWith("org.jdom")

  }

  /**
   * Whether the analysis must not go into the implementations of [method].
   * This is true if a JDK class or one of [LockReqRules.opaqueClasses] declares [method].
   * This is also true for `Disposable.dispose` and all its overrides.
   */
  context(patterns: LockReqRules)
  fun isOpaqueMethod(method: PsiMethod): Boolean {
    val containingClass = method.containingClass ?: return false
    return containingClass.qualifiedName in patterns.opaqueClasses || isJdkClass(containingClass) || isDisposeMethod(method, containingClass)
  }

  context(patterns: LockReqRules)
  private fun isDisposeMethod(method: PsiMethod, containingClass: PsiClass): Boolean {
    return method.name in patterns.disposeMethodNames &&
           method.parameterList.parametersCount == 0 &&
           InheritanceUtil.isInheritor(containingClass, patterns.disposableInterfaceFqn)
  }

  private fun isJdkClass(psiClass: PsiClass): Boolean {
    val file = psiClass.containingFile?.virtualFile ?: return false
    return ProjectFileIndex.getInstance(psiClass.project).getOrderEntriesForFile(file).any { it is JdkOrderEntry }
  }

  context(config: AnalysisConfig, patterns: LockReqRules)
  fun isCommonMethod(method: PsiMethod): Boolean {
    return (method.name == "compute" && method.containingClass?.qualifiedName == "com.intellij.openapi.util.ThrowableComputable") || method.name in patterns.commonMethods
  }


}