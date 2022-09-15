// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.codeInsight.PsiOnlyKotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.lineMarkers.run.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.CallableReturnTypeUpdaterApplicator
import org.jetbrains.kotlin.psi.KtNamedFunction

private val detectorConfiguration = KotlinMainFunctionDetector.Configuration(checkResultType = false)

class MainFunctionReturnUnitInspection : AbstractKotlinApplicatorBasedInspection<KtNamedFunction, CallableReturnTypeUpdaterApplicator.TypeInfo>(KtNamedFunction::class) {
    override fun getApplicabilityRange() = ApplicabilityRanges.CALLABLE_RETURN_TYPE

    override fun getApplicator(): KotlinApplicator<KtNamedFunction, CallableReturnTypeUpdaterApplicator.TypeInfo> {
        return CallableReturnTypeUpdaterApplicator.applicator.with {
            isApplicableByPsi { function ->
                PsiOnlyKotlinMainFunctionDetector.isMain(function, detectorConfiguration)
                        && (function.hasDeclaredReturnType() || function.equalsToken != null)
            }
            familyName { KotlinBundle.message("change.main.function.return.type.to.unit.fix.text2") }
            actionName { callable, _ ->
                if (callable.typeReference != null)
                    KotlinBundle.message("change.main.function.return.type.to.unit.fix.text2")
                else
                    KotlinBundle.message("change.main.function.return.type.to.unit.fix.text")
            }
        }
    }

    override fun getInputProvider(): KotlinApplicatorInputProvider<KtNamedFunction, CallableReturnTypeUpdaterApplicator.TypeInfo> {
        return inputProvider { function ->
            if (KotlinMainFunctionDetector.getInstance().isMain(function, detectorConfiguration)) {
                analyze(function) {
                    if (!function.getFunctionLikeSymbol().returnType.isUnit) {
                        return@inputProvider CallableReturnTypeUpdaterApplicator.TypeInfo(CallableReturnTypeUpdaterApplicator.TypeInfo.UNIT)
                    }
                }
            }

            return@inputProvider null
        }
    }
}