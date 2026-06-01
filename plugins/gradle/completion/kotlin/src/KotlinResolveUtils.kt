// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.util.asSafely
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaSingleCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression

internal val GRADLE_DEPENDENCY_HANDLER_CLASS_ID = ClassId.topLevel(FqName("org.gradle.api.artifacts.dsl.DependencyHandler"))
internal val GRADLE_DEPENDENCY_CLASS_ID = ClassId.topLevel(FqName("org.gradle.api.artifacts.Dependency"))

// TODO Move to a separate module in Gradle plugin for Kotlin resolution. Reuse in kotlinGradleTaskUtils.kt
/**
 * Checks if call expression name is in [callNames]. If not, returns false early.
 * Then, if in dumb-mode, just returns true, otherwise, further checks if it's a subtype of [receiverFqn].
 */
internal fun KtCallExpression.isCallWithReceiverSubtypeDumbAware(receiverFqn: FqName, callNames: Set<String>): Boolean {
  val callName = this.calleeExpression?.text ?: return false
  if (callName !in callNames) return false
  if (DumbService.isDumb(this.project)) return true
  return this.isReceiverSubtypeOf(receiverFqn)
}

@OptIn(KaExperimentalApi::class)
private fun KtCallExpression.isReceiverSubtypeOf(supertypeFqn: FqName): Boolean {
  val callExpression = this
  analyze(callExpression) {
    val supertype = buildClassType(ClassId.topLevel(supertypeFqn))
    val functionCall = callExpression.resolveToCall()?.singleFunctionCallOrNull()
    if (functionCall == null) {
      // An expression might not be resolved to a single call due to ambiguity - e.g. when inputting arguments is not finished yet.
      return callExpression.resolveToCallCandidates().any { candidateInfo ->
        val candidateCall = candidateInfo.candidate.asSafely<KaSingleCall<*, *>>() ?: return@any false
        isReceiverForCallASubtypeOf(candidateCall, supertype)
      }
    }
    else {
      return isReceiverForCallASubtypeOf(functionCall, supertype)
    }
  }
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.isReceiverForCallASubtypeOf(call: KaSingleCall<*, *>, supertype: KaType): Boolean {
  val receiverType = call.extensionReceiver?.type
                     ?: call.dispatchReceiver?.type
                     ?: return false
  val unwrappedType = if (receiverType is KaFlexibleType) receiverType.lowerBound else receiverType
  return unwrappedType.isSubtypeOf(supertype)
}

/**
 * Returns trues if in the specified [context] a PSI call expression with the given [methodName] could be resolved.
 */
internal fun isNameResolvableToMethod(
  methodName: String,
  returnType: ClassId,
  receiverType: ClassId,
  context: PsiElement,
): Boolean {
  val ktBlock = context.parentOfType<KtBlockExpression>() ?: return false
  val ktFile = ktBlock.containingKtFile
  return analyze(ktBlock) {
    val scope = ktFile.scopeContext(ktBlock).compositeScope()
    scope.callables(Name.identifier(methodName)).any { callable ->
      callable is KaNamedFunctionSymbol
      && callable.name.asString() == methodName
      && callable.receiverType?.isSubtypeOf(receiverType) == true
      && callable.returnType.isSubtypeOf(returnType)
    }
  }
}