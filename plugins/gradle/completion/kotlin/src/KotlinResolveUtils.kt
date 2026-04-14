// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.util.asSafely
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaSingleCall
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression

// TODO Move to a separate module in Gradle plugin for Kotlin resolution. Reuse in kotlinGradleTaskUtils.kt
internal fun KtCallExpression.isCallWithReceiverSubtype(receiverFqn: FqName, callNames: Set<String>): Boolean {
    val callName = this.calleeExpression?.text ?: return false
    if (callName !in callNames) return false
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
                val candidateCall = candidateInfo.candidate.asSafely<KaSingleCall<*,*>>() ?: return@any false
                isReceiverForCallASubtypeOf(candidateCall, supertype)
            }
        } else {
            return isReceiverForCallASubtypeOf(functionCall, supertype)
        }
    }
}

@OptIn(KaExperimentalApi::class)
private fun KaSession.isReceiverForCallASubtypeOf(call: KaSingleCall<*,*>, supertype: KaType): Boolean {
    val receiverType = call.extensionReceiver?.type
        ?: call.dispatchReceiver?.type
        ?: return false
    val unwrappedType = if (receiverType is KaFlexibleType) receiverType.lowerBound else receiverType
    return unwrappedType.isSubtypeOf(supertype)
}