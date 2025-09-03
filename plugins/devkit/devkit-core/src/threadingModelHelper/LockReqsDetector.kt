// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod

class LockReqsDetector(private val patterns: LockReqsRules = DefaultLockReqsRules()) {

  fun findAnnotationRequirements(method: PsiMethod): List<LockRequirement> {
    val requirements = mutableListOf<LockRequirement>()
    method.annotations.forEach { annotation ->
      patterns.lockAnnotations[annotation.qualifiedName]?.let { lockType ->
        requirements.add(LockRequirement(method, lockType, RequirementReason.ANNOTATION))
      }
    }
    return requirements
  }

  fun findBodyRequirements(method: PsiMethod): List<LockRequirement> {
    val requirements = mutableListOf<LockRequirement>()
    val className = method.containingClass?.qualifiedName
    val methodName = method.name
    patterns.assertionMethods[className]?.get(methodName)?.let { lockType ->
      requirements.add(LockRequirement(method, lockType, RequirementReason.ASSERTION))
    }
    if (isSwingMethod(method)) {
      requirements.add(LockRequirement(method, LockType.EDT, RequirementReason.SWING_COMPONENT))
    }
    return requirements
  }

  private fun isSwingMethod(method: PsiMethod): Boolean {
    val containingClass = method.containingClass ?: return false
    val className = containingClass.qualifiedName ?: return false
    if (isSwingClass(className)) return method.name !in patterns.safeSwingMethods
    return LockReqsPsiOps.inheritsFromAny(containingClass, patterns.edtRequiredClasses)
  }

  private fun isSwingClass(className: String): Boolean {
    return patterns.edtRequiredClasses.contains(className) ||
           LockReqsPsiOps.isInPackages(className, patterns.edtRequiredPackages)
  }

  fun isAsyncDispatch(method: PsiMethod): Boolean {
    return method.name in patterns.asyncMethods &&
           method.containingClass?.qualifiedName in patterns.asyncClasses
  }

  fun isMessageBusCall(method: PsiMethod): Boolean {
    val containingClass = method.containingClass?.qualifiedName ?: return false
    return patterns.messageBusClasses.contains(containingClass) &&
           method.name in patterns.messageBusSyncMethods
  }

  fun extractMessageBusTopic(method: PsiMethod): PsiClass? {
    if (method.name in patterns.messageBusSyncMethods) {
      return LockReqsPsiOps.resolveReturnType(method)
    }
    return null
  }

  fun isCommonMethod(method: PsiMethod): Boolean {
    return method.name in patterns.commonMethods
  }
}