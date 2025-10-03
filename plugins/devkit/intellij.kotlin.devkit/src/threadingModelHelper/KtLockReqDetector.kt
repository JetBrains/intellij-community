// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.threadingModelHelper

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.idea.devkit.threadingModelHelper.*

class KtLockReqDetector(private val patterns: LockReqRules = BaseLockReqRules()) : LockReqDetector {

  private val psiOps = KtLockReqPsiOps()

  override fun findAnnotationRequirements(method: PsiMethod): List<LockRequirement> {
    val requirements = mutableListOf<LockRequirement>()
    method.annotations.forEach { annotation ->
      patterns.lockAnnotations[annotation.qualifiedName]?.let { lockType ->
        requirements.add(LockRequirement(method, lockType, RequirementReason.ANNOTATION))
      }
    }
    return requirements
  }

  override fun findBodyRequirements(method: PsiMethod): List<LockRequirement> {
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
    return psiOps.inheritsFromAny(containingClass, patterns.edtRequiredClasses)
  }

  private fun isSwingClass(className: String): Boolean {
    return patterns.edtRequiredClasses.contains(className) ||
           psiOps.isInPackages(className, patterns.edtRequiredPackages)
  }

  override fun isAsyncDispatch(method: PsiMethod): Boolean {
    return method.name in patterns.asyncMethods &&
           method.containingClass?.qualifiedName in patterns.asyncClasses
  }

  override fun isMessageBusCall(method: PsiMethod): Boolean {
    val containingClass = method.containingClass?.qualifiedName ?: return false
    return patterns.messageBusClasses.contains(containingClass) &&
           method.name in patterns.messageBusSyncMethods
  }

  override fun extractMessageBusTopic(method: PsiMethod): PsiClass? {
    if (method.name in patterns.messageBusSyncMethods) {
      return psiOps.resolveReturnType(method)
    }
    return null
  }

  fun isCommonMethod(method: PsiMethod): Boolean {
    return method.name in patterns.commonMethods
  }
}