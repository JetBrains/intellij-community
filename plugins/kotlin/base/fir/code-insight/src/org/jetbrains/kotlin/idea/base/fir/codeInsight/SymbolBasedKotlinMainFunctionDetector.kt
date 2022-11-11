// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.lineMarkers.run.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.KotlinPsiHeuristics
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction

internal class SymbolBasedKotlinMainFunctionDetector : KotlinMainFunctionDetector {
    override fun isMain(function: KtNamedFunction): Boolean {
        if (function.isLocal) {
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
        } else if (parameterCount != 1) {
            return false
        }

        if ((KotlinPsiHeuristics.findJvmName(function) ?: function.name) != "main") {
            return false
        }

        if (!isTopLevel && !KotlinPsiHeuristics.hasJvmStaticAnnotation(function)) {
            return false
        }

        return true
    }

    override fun hasMain(declarations: List<KtDeclaration>): Boolean {
        if (declarations.isEmpty()) {
            return false
        }

        return declarations.any { it is KtNamedFunction && isMain(it) }
    }
}
