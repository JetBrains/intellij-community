// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.K2EnableUnsupportedFeatureFix
import java.util.*

object UnsupportedFeatureFixFactory {
    val unsupportedFeature: KotlinQuickFixFactory.IntentionBased<KaFirDiagnostic.UnsupportedFeature> =
        KotlinQuickFixFactory.IntentionBased { diagnostic ->
            val languageFeature = diagnostic.unsupportedFeature.first
            val module = diagnostic.psi.module ?: return@IntentionBased emptyList()

            val featureMinLanguageVersion = LanguageFeatureSupportWhiteList[languageFeature] ?: return@IntentionBased emptyList()
            val moduleLanguageVersion = module.languageVersionSettings.languageVersion
            if (featureMinLanguageVersion > moduleLanguageVersion) return@IntentionBased emptyList()

            listOf(
                K2EnableUnsupportedFeatureFix(diagnostic.psi, module, languageFeature)
            )
        }

    /**
     * Versions for language features at which they can be enabled via a feature flag.
     * It is a version at which the compiler starts supporting the flag — it is generally lower than [LanguageFeature.sinceVersion].
     */
    private val LanguageFeatureSupportWhiteList: Map<LanguageFeature, LanguageVersion> =
        EnumMap<LanguageFeature, LanguageVersion>(LanguageFeature::class.java).apply {
            put(LanguageFeature.MultiDollarInterpolation, LanguageVersion.KOTLIN_2_1)
            put(LanguageFeature.WhenGuards, LanguageVersion.KOTLIN_2_1)
            put(LanguageFeature.BreakContinueInInlineLambdas, LanguageVersion.KOTLIN_2_1)
            put(LanguageFeature.ContextParameters, LanguageVersion.KOTLIN_2_2) // The -X flag was added in 2.1.20
            put(LanguageFeature.AnnotationAllUseSiteTarget, LanguageVersion.KOTLIN_2_2)
        }
}
