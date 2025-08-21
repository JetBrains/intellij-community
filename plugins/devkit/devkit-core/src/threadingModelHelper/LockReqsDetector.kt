// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.threadingModelHelper

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.InheritanceUtil

class LockReqsDetector(private val patterns: LockReqsPatterns = DefaultLockReqsPatterns()) {

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
    if (isSwingMethod(method)) requirements.add(LockRequirement(method, LockType.EDT, RequirementReason.SWING_COMPONENT))
    return requirements
  }

  private fun isSwingComponent(className: String): Boolean {
    return patterns.edtRequiredClasses.contains(className) ||
           patterns.edtRequiredPackages.any { className.startsWith("$it.") }
  }

  private fun isSwingMethod(method: PsiMethod): Boolean {
    val containingClass = method.containingClass ?: return false
    val className = containingClass.qualifiedName ?: return false
    if (isSwingComponent(className)) return method.name !in patterns.safeSwingMethods
    return patterns.edtRequiredClasses.any { edtClass ->
      InheritanceUtil.isInheritor(containingClass, edtClass)
    }
  }

  fun isAsyncBoundary(method: PsiMethod): Boolean {
    return method.name in patterns.asyncMethods || method.containingClass?.qualifiedName in patterns.asyncClasses
  }

  fun isMessageBusCall(expression: PsiMethodCallExpression): Boolean {
    val method = expression.resolveMethod() ?: return false
    val containingClass = method.containingClass?.qualifiedName ?: return false
    return patterns.messageBusClasses.contains(containingClass) && method.name in patterns.messageBusMethods
  }

  fun extractMessageBusTopic(expression: PsiMethodCallExpression): String? {
    val method = expression.resolveMethod() ?: return null
    if (method.name == "syncPublisher") {
      val returnType = method.returnType as? PsiClassType ?: return null
      val resolvedTopicInterface = returnType.resolve() ?: return null
      return resolvedTopicInterface.qualifiedName
    }
    return null
  }

  fun isPolymorphicCall(method: PsiMethod): Boolean {
    if (listOf(PsiModifier.FINAL, PsiModifier.STATIC, PsiModifier.PRIVATE).any {
        method.hasModifierProperty(it)
      }) return false
    val containingClass = method.containingClass ?: return false
    return !containingClass.hasModifierProperty(PsiModifier.FINAL)
  }
}