// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.threadingModelHelper

import org.jetbrains.idea.devkit.threadingModelHelper.*
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.idea.references.mainReference

class KtLockReqDetector(private val patterns: LockReqRules = BaseLockReqRules()) {

  private val psiOps = KtLockReqPsiOps()

  fun findAnnotationRequirements(function: KtNamedFunction): List<LockRequirement> {
    val requirements = mutableListOf<LockRequirement>()
    function.annotationEntries.forEach { annotation ->
      val qualifiedName = resolveAnnotationName(annotation)
      patterns.lockAnnotations[qualifiedName]?.let { lockType ->
        requirements.add(LockRequirement(function, lockType, RequirementReason.ANNOTATION))
      }
    }
    return requirements
  }

  private fun resolveAnnotationName(annotation: KtAnnotationEntry): String? {
    return when (val callee = annotation.calleeExpression) {
      is KtNameReferenceExpression -> {
        analyze(callee) {
          val symbol = callee.mainReference.resolveToSymbol() as? KaClassSymbol
          symbol?.classId?.asFqNameString()
        }
      }
      is KtDotQualifiedExpression -> callee.text
      else -> null
    }
  }

  fun findBodyRequirements(function: KtNamedFunction): List<LockRequirement> {
    val requirements = mutableListOf<LockRequirement>()
    val className = function.containingClassOrObject?.fqName?.asString()
    val methodName = function.name

    patterns.assertionMethods[className]?.get(methodName)?.let { lockType ->
      requirements.add(LockRequirement(function, lockType, RequirementReason.ASSERTION))
    }

    if (isSwingMethod(function)) {
      requirements.add(LockRequirement(function, LockType.EDT, RequirementReason.SWING_COMPONENT))
    }
    return requirements
  }

  private fun isSwingMethod(function: KtNamedFunction): Boolean {
    val containingClass = function.containingClassOrObject ?: return false
    val className = containingClass.fqName?.asString() ?: return false

    if (isSwingClass(className)) {
      return function.name !in patterns.safeSwingMethods
    }
    return psiOps.inheritsFromAny(containingClass, patterns.edtRequiredClasses)
  }

  private fun isSwingClass(className: String): Boolean {
    return patterns.edtRequiredClasses.contains(className) ||
           psiOps.isInPackages(className, patterns.edtRequiredPackages)
  }

  fun isAsyncDispatch(call: KtCallExpression): Boolean {
    val callName = call.calleeExpression?.text ?: return false
    val receiverType = psiOps.getReceiverType(call)

    return callName in patterns.asyncMethods &&
           receiverType in patterns.asyncClasses
  }

  fun isMessageBusCall(call: KtCallExpression): Boolean {
    val receiverType = psiOps.getReceiverType(call) ?: return false
    val callName = call.calleeExpression?.text ?: return false

    return patterns.messageBusClasses.contains(receiverType) &&
           callName in patterns.messageBusSyncMethods
  }

  fun extractMessageBusTopic(call: KtCallExpression): KtClass? {
    val callName = call.calleeExpression?.text ?: return null
    if (callName in patterns.messageBusSyncMethods) {
      return psiOps.resolveReturnType(call)
    }
    return null
  }

  fun isCoroutineDispatch(call: KtCallExpression): Boolean {
    val callName = call.calleeExpression?.text ?: return false
    val receiverType = psiOps.getReceiverType(call)

    return true
  }

  fun isCommonMethod(function: KtNamedFunction): Boolean {
    return function.name in patterns.commonMethods
  }
}