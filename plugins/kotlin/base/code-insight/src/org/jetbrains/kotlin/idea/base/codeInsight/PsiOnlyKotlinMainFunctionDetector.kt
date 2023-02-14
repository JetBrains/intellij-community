// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtNamedFunction

@ApiStatus.Internal
object PsiOnlyKotlinMainFunctionDetector : KotlinMainFunctionDetector {
    @RequiresReadLock
    override fun isMain(function: KtNamedFunction, configuration: KotlinMainFunctionDetector.Configuration): Boolean {
        if (function.isLocal || function.typeParameters.isNotEmpty()) {
            return false
        }

        val isTopLevel = function.isTopLevel
        val parameterCount = function.valueParameters.size + (if (function.receiverTypeReference != null) 1 else 0)

        if (parameterCount == 0) {
            if (!isTopLevel) {
                return false
            }

            if (!function.languageVersionSettings.supportsFeature(LanguageFeature.ExtendedMainConvention)) {
                return false
            }
        } else if (parameterCount == 1) {
            if (configuration.checkParameterType && !isMainCheckParameter(function)) {
                return false
            }
        } else {
            return false
        }

        if ((KotlinPsiHeuristics.findJvmName(function) ?: function.name) != "main") {
            return false
        }

        if (!isTopLevel && configuration.checkJvmStaticAnnotation && !KotlinPsiHeuristics.hasJvmStaticAnnotation(function)) {
            return false
        }

        if (configuration.checkResultType) {
            val returnTypeReference = function.typeReference
            if (returnTypeReference != null && !KotlinPsiHeuristics.typeMatches(returnTypeReference, StandardClassIds.Unit)) {
                return false
            }
        }

        return true
    }

    private fun isMainCheckParameter(function: KtNamedFunction): Boolean {
        val receiverTypeReference = function.receiverTypeReference
        if (receiverTypeReference != null) {
            return KotlinPsiHeuristics.typeMatches(receiverTypeReference, StandardClassIds.Array, StandardClassIds.String)
        }

        val parameter = function.valueParameters.singleOrNull() ?: return false
        val parameterTypeReference = parameter.typeReference ?: return false

        return when {
            parameter.isVarArg -> KotlinPsiHeuristics.typeMatches(parameterTypeReference, StandardClassIds.String)
            else -> KotlinPsiHeuristics.typeMatches(parameterTypeReference, StandardClassIds.Array, StandardClassIds.String)
        }
    }
}