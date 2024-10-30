// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticWithParameters1
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticParameterRenderer
import org.jetbrains.kotlin.diagnostics.rendering.RenderingContext
import org.jetbrains.kotlin.idea.base.fe10.highlighting.KotlinBaseFe10HighlightingBundle
import java.util.*

internal val UNSUPPORTED_K2_BETA_FEATURES: EnumSet<LanguageFeature> = EnumSet.of(
    LanguageFeature.MultiDollarInterpolation,
    LanguageFeature.BreakContinueInInlineLambdas,
    LanguageFeature.WhenGuards,
)

/**
 * A parameter renderer that replaces the default UNSUPPORTED_FEATURE description.
 *
 * The default message can be confusing for language features without a stated version
 * because the suggestion to enable a feature via a feature flag
 * can be given even when the flag has been used in the project.
 */
internal class DecoratedRendererForK2Features(
    private val defaultRenderer: DiagnosticParameterRenderer<Pair<LanguageFeature, LanguageVersionSettings>>
) : DiagnosticParameterRenderer<Pair<LanguageFeature, LanguageVersionSettings>> {

    override fun render(
        obj: Pair<LanguageFeature, LanguageVersionSettings>,
        renderingContext: RenderingContext
    ): String {
        val languageFeature = obj.first
        if (languageFeature !in UNSUPPORTED_K2_BETA_FEATURES) return defaultRenderer.render(obj, renderingContext)

        return KotlinBaseFe10HighlightingBundle.message(
            "the.feature.0.is.not.supported.in.k1.mode",
            languageFeature.quotedPresentableName(),
        )
    }
}

internal fun Diagnostic.unsupportedFeatureOrNull(): LanguageFeature? {
    if (this !is DiagnosticWithParameters1<*, *>) return null
    val diagnosticParameter = this.a as? Pair<*, *> ?: return null
    val (languageFeature, _) = diagnosticParameter
    if (languageFeature !is LanguageFeature) return null
    return languageFeature
}

internal fun LanguageFeature.quotedPresentableName(): String = "'${presentableName}'"
