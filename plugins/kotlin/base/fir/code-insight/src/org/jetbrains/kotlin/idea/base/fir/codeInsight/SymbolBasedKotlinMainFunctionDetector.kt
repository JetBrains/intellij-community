// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.codeInsight

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KtConstantAnnotationValue
import org.jetbrains.kotlin.analysis.api.annotations.annotationsByClassId
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.base.KtConstantValue
import org.jetbrains.kotlin.analysis.api.components.buildClassType
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtAnnotatedSymbol
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.analysis.api.types.KtTypeNullability
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.types.Variance

internal class SymbolBasedKotlinMainFunctionDetector : KotlinMainFunctionDetector {
    override fun isMain(function: KtNamedFunction, configuration: KotlinMainFunctionDetector.Configuration): Boolean {
        if (function.isLocal || function.typeParameters.isNotEmpty()) {
            return false
        }

        val isTopLevel = function.isTopLevel
        val parameterCount = function.valueParameters.size + (if (function.receiverTypeReference != null) 1 else 0)

        if (parameterCount == 0) {
            if (!isTopLevel || !function.languageVersionSettings.supportsFeature(LanguageFeature.ExtendedMainConvention)) {
                return false
            }
        } else if (parameterCount > 1) {
            return false
        }

        analyze(function) {
            if (parameterCount == 1 && configuration.checkParameterType) {
                val parameterTypeReference = function.receiverTypeReference
                    ?: function.valueParameters[0].typeReference
                    ?: return false

                val parameterType = parameterTypeReference.getKtType()
                if (!parameterType.isSubTypeOf(buildMainParameterType())) {
                    return false
                }
            }

            val functionSymbol = function.getFunctionLikeSymbol()
            if (functionSymbol !is KtFunctionSymbol) {
                return false
            }

            val jvmName = getJvmName(functionSymbol) ?: functionSymbol.name.asString()
            if (jvmName != "main") {
                return false
            }

            if (configuration.checkResultType && !function.getReturnKtType().isUnit) {
                return false
            }

            if (!isTopLevel && configuration.checkJvmStaticAnnotation) {
                if (!functionSymbol.hasAnnotation(StandardClassIds.Annotations.JvmStatic)) {
                    return false
                }
            }
        }

        return true
    }

    private fun KtAnalysisSession.buildMainParameterType(): KtType {
        return buildClassType(StandardClassIds.Array) {
            val argumentType = buildClassType(StandardClassIds.String) {
                nullability = KtTypeNullability.NULLABLE
            }

            argument(argumentType, Variance.OUT_VARIANCE)
            nullability = KtTypeNullability.NULLABLE
        }
    }

    private fun getJvmName(symbol: KtAnnotatedSymbol): String? {
        val jvmNameAnnotation = symbol.annotationsByClassId(StandardClassIds.Annotations.JvmName).firstOrNull() ?: return null
        val annotationValue = jvmNameAnnotation.arguments.singleOrNull()?.expression as? KtConstantAnnotationValue ?: return null
        val stringValue = annotationValue.constantValue as? KtConstantValue.KtStringConstantValue ?: return null
        return stringValue.value
    }
}