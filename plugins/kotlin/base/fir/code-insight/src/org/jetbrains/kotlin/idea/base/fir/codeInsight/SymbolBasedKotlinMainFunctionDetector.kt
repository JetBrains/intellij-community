// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight

import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getJvmName
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.types.Variance

internal class SymbolBasedKotlinMainFunctionDetector : KotlinMainFunctionDetector {
    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun isMain(function: KtNamedFunction, configuration: KotlinMainFunctionDetector.Configuration): Boolean {
        if (function.isLocal || function.typeParameters.isNotEmpty()) {
            return false
        }

        val supportsExtendedMainConvention = function.languageVersionSettings.supportsFeature(LanguageFeature.ExtendedMainConvention)

        val isTopLevel = function.isTopLevel
        val parameterCount = function.valueParameters.size + (if (function.receiverTypeReference != null) 1 else 0)

        if (parameterCount == 0) {
            if (!isTopLevel || !configuration.allowParameterless || !supportsExtendedMainConvention) {
                return false
            }
        } else if (parameterCount > 1) {
            return false
        }

        // TODO find a better solution to avoid calling `isMain` from EDT
        allowAnalysisOnEdt {
            analyze(function) {
                if (parameterCount == 1 && configuration.checkParameterType) {
                    val parameterTypeReference = function.receiverTypeReference
                        ?: function.valueParameters[0].typeReference
                        ?: return false

                    val parameterType = parameterTypeReference.getKtType()
                    if (!parameterType.isResolvedClassType() || !parameterType.isSubTypeOf(buildMainParameterType())) {
                        return false
                    }
                }

                val functionSymbol = function.getFunctionLikeSymbol()
                if (functionSymbol !is KtFunctionSymbol) {
                    return false
                }

                val jvmName = getJvmName(functionSymbol) ?: functionSymbol.name.asString()
                if (jvmName != KotlinMainFunctionDetector.MAIN_FUNCTION_NAME) {
                    return false
                }

                if (configuration.checkResultType && !function.getReturnKtType().isUnit) {
                    return false
                }

                if (!isTopLevel) {
                    val containingClass = functionSymbol.originalContainingClassForOverride ?: return false
                    val annotationJvmStatic = JvmStandardClassIds.Annotations.JvmStatic
                    return containingClass.classKind.isObject
                            && (!configuration.checkJvmStaticAnnotation || functionSymbol.hasAnnotation(annotationJvmStatic))

                }

                if (parameterCount == 0) {
                    // We do not support parameterless entry points having JvmName("name") but different real names
                    // See more at https://github.com/Kotlin/KEEP/blob/master/proposals/enhancing-main-convention.md#parameterless-main
                    if (function.name.toString() != KotlinMainFunctionDetector.MAIN_FUNCTION_NAME) return false

                    val functionsInFile = function.containingKtFile.declarations.filterIsInstance<KtNamedFunction>()
                    // Parameterless function is considered as an entry point only if there's no entry point with an array parameter
                    if (functionsInFile.any { isMain(it, configuration.with { allowParameterless = false }) }) {
                        return false
                    }
                }
            }
        }

        return true
    }

    context(KtAnalysisSession)
    private fun buildMainParameterType(): KtType {
        return buildClassType(StandardClassIds.Array) {
            val argumentType = buildClassType(StandardClassIds.String) {
                nullability = KtTypeNullability.NON_NULLABLE
            }

            argument(argumentType, Variance.OUT_VARIANCE)
            nullability = KtTypeNullability.NULLABLE
        }
    }

    context(KtAnalysisSession)
    private fun KtType.isResolvedClassType(): Boolean = when (this) {
        is KtNonErrorClassType -> ownTypeArguments.mapNotNull { it.type }.all { it.isResolvedClassType() }
        else -> false
    }
}