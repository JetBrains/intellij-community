// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageFeature.PropertyParamAnnotationDefaultTargetMode
import org.jetbrains.kotlin.config.toKotlinVersion
import org.jetbrains.kotlin.diagnostics.rendering.buildRuntimeFeatureToFlagMap
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion

object CliArgumentStringBuilder {
    const val LANGUAGE_FEATURE_FLAG_PREFIX: String = "-XXLanguage:"

    private val dedicatedFeatureFlags: Map<LanguageFeature, String> by lazy {
        buildRuntimeFeatureToFlagMap(this::class.java.classLoader)
    }

    private val featuresWithComplexArguments: Map<Pair<LanguageFeature, LanguageFeature.State>, String> = mapOf(
        (PropertyParamAnnotationDefaultTargetMode to LanguageFeature.State.ENABLED) to "-Xannotation-default-target=param-property",
        (PropertyParamAnnotationDefaultTargetMode to LanguageFeature.State.DISABLED) to "-Xannotation-default-target=first-only-warn",
    )

    private val LanguageFeature.dedicatedFlagInfo: Pair<String, KotlinVersion?>?
        get()  {
            val flag = dedicatedFeatureFlags[this] ?: return null
            return flag to this.sinceVersion?.toKotlinVersion()
        }

    private val LanguageFeature.State.sign: String
        get() = when (this) {
            LanguageFeature.State.ENABLED -> "+"
            LanguageFeature.State.DISABLED -> "-"
        }

    private fun LanguageFeature.getFeatureMentionInCompilerArgsRegex(): Regex {
        val basePattern = "$LANGUAGE_FEATURE_FLAG_PREFIX(?:-|\\+)$name"
        val fullPattern = dedicatedFlagInfo?.let { (dedicatedFlag, _) -> "(?:$basePattern)|$dedicatedFlag" } ?: basePattern

        return Regex(fullPattern)
    }

    fun LanguageFeature.buildArgumentString(state: LanguageFeature.State, kotlinVersion: IdeKotlinVersion?): String {
        val shouldBeFeatureEnabled = state == LanguageFeature.State.ENABLED
        val dedicatedFlag = dedicatedFlagInfo?.run {
            val (xFlag, xFlagSinceVersion) = this
            if (kotlinVersion == null || xFlagSinceVersion == null || kotlinVersion.kotlinVersion >= xFlagSinceVersion) xFlag else null
        }
        val specialCompilerArgument = featuresWithComplexArguments[this to state]

        return when {
            shouldBeFeatureEnabled && dedicatedFlag != null -> dedicatedFlag
            specialCompilerArgument != null -> specialCompilerArgument
            else -> "$LANGUAGE_FEATURE_FLAG_PREFIX${state.sign}$name"
        }
    }

    /**
     *  prefix is used only when there wasn't any value for this parameter before
     *  postfix is used to correctly split arguments if we replace/add value to the existing collection
     */
    fun String.replaceLanguageFeature(
        feature: LanguageFeature,
        state: LanguageFeature.State,
        kotlinVersion: IdeKotlinVersion?,
        prefix: String = "",
        postfix: String = "",
        separator: String = ", ",
        quoted: Boolean = true
    ): String {
        val quote = if (quoted) "\"" else ""
        val featureArgumentString = feature.buildArgumentString(state, kotlinVersion)
        val existingFeatureMatchResult = feature.getFeatureMentionInCompilerArgsRegex().find(this)

        return if (existingFeatureMatchResult != null) {
            replace(existingFeatureMatchResult.value, featureArgumentString)
        } else {
            val splitText = if (postfix.isNotEmpty()) split(postfix) else listOf(this, "")
            // Split by `)` or `))` or some other postfix to add a new value between the old value and such brackets.
            if (splitText.size != 2) {
                "$prefix$quote$featureArgumentString$quote$postfix"
            } else {
                val (mainPart, commentPart) = splitText
                val newArgumentString = "$separator$quote$featureArgumentString$quote"
                // In Groovy / Kotlin DSL, we can have comment after [...] or listOf(...)
                mainPart + newArgumentString + postfix + commentPart
            }
        }
    }
}
