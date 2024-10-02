// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion

object CliArgumentStringBuilder {
    const val LANGUAGE_FEATURE_FLAG_PREFIX = "-XXLanguage:"
    private const val LANGUAGE_FEATURE_DEDICATED_FLAG_PREFIX = "-X"

    private val LanguageFeature.dedicatedFlagInfo
        get() = when (this) {
            LanguageFeature.InlineClasses -> Pair("inline-classes", KotlinVersion(1, 3, 50))
            else -> null
        }

    private val LanguageFeature.State.sign: String
        get() = when (this) {
            LanguageFeature.State.ENABLED -> "+"
            LanguageFeature.State.DISABLED -> "-"
            LanguageFeature.State.ENABLED_WITH_WARNING -> "+" // not supported normally
        }

    private fun LanguageFeature.getFeatureMentionInCompilerArgsRegex(): Regex {
        val basePattern = "$LANGUAGE_FEATURE_FLAG_PREFIX(?:-|\\+)$name"
        val fullPattern =
            if (dedicatedFlagInfo != null) "(?:$basePattern)|$LANGUAGE_FEATURE_DEDICATED_FLAG_PREFIX${dedicatedFlagInfo!!.first}" else basePattern

        return Regex(fullPattern)
    }

    fun LanguageFeature.buildArgumentString(state: LanguageFeature.State, kotlinVersion: IdeKotlinVersion?): String {
        val shouldBeFeatureEnabled = state == LanguageFeature.State.ENABLED || state == LanguageFeature.State.ENABLED_WITH_WARNING
        val dedicatedFlag = dedicatedFlagInfo?.run {
            val (xFlag, xFlagSinceVersion) = this
            if (kotlinVersion == null || kotlinVersion.kotlinVersion >= xFlagSinceVersion) xFlag else null
        }

        return if (shouldBeFeatureEnabled && dedicatedFlag != null) {
            LANGUAGE_FEATURE_DEDICATED_FLAG_PREFIX + dedicatedFlag
        } else {
            "$LANGUAGE_FEATURE_FLAG_PREFIX${state.sign}$name"
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
            if (splitText.size != 2) {
                "$prefix$quote$featureArgumentString$quote$postfix"
            } else {
                val (mainPart, commentPart) = splitText
                // In Groovy / Kotlin DSL, we can have comment after [...] or listOf(...)
                mainPart + "$separator$quote$featureArgumentString$quote$postfix" + commentPart
            }
        }
    }
}
