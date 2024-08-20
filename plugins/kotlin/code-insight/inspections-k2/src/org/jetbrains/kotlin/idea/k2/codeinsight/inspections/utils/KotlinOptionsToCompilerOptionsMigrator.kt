// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.utils

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import java.util.function.Function

internal data class Replacement(val expressionToReplace: KtExpression, val replacement: String, val classToImport: FqName? = null)

internal fun getReplacementForOldKotlinOptionIfNeeded(binaryExpression: KtBinaryExpression): Replacement? {

    val rightPartOfBinaryExpression = binaryExpression.right
    val textOfRightPartOfBinaryExpression = rightPartOfBinaryExpression?.text ?: return null

    val leftPartOfBinaryExpression = binaryExpression.left ?: return null

    val textOfLeftPartOfBinaryExpression = leftPartOfBinaryExpression.text

    // We don't touch strings like `freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"`
    if (rightPartOfBinaryExpression is KtBinaryExpression) {
        return getReplacementOnlyOfKotlinOptionsIfNeeded(binaryExpression, textOfLeftPartOfBinaryExpression)
    }

    var hasKotlinOptionsInDotQualifiedExpression = false
    val optionName = when (leftPartOfBinaryExpression) {
      is KtDotQualifiedExpression -> {
          val partBeforeDot = leftPartOfBinaryExpression.receiverExpression.text
          if (partBeforeDot != "kotlinOptions") return null
          hasKotlinOptionsInDotQualifiedExpression = true
          leftPartOfBinaryExpression.getCalleeExpressionIfAny()?.text ?: return null
      }

        is KtReferenceExpression -> {
            textOfLeftPartOfBinaryExpression
      }

        else -> {
            return null
      }
    }

    val optionValue = if (optionName != "freeCompilerArgs" && rightPartOfBinaryExpression is KtStringTemplateExpression) {
        rightPartOfBinaryExpression.text.removeSurrounding("\"", "\"")
    } else {
        textOfRightPartOfBinaryExpression
    }

    val operationReference = binaryExpression.operationReference.text
    val operationReplacer = when (operationReference) {
        "=" -> {
            "set"
        }

        "+=" -> {
            if (textOfRightPartOfBinaryExpression.contains("listOf")) {
                "addAll"
            } else {
                "add"
            }
        }

        else -> {
            null
        }
    }

    if (operationReplacer != null) {
        val replacement = StringBuilder()
        if (hasKotlinOptionsInDotQualifiedExpression) replacement.append("compilerOptions.")

        // jvmTarget, apiVersion and languageVersion
        val versionOptionData = optionsWithValuesMigratedFromNumericStringsToEnums[optionName]
        if (versionOptionData != null) {
            val convertedValue = versionOptionData.mappingRule.apply(optionValue)
            if (convertedValue != null) {
                replacement.append(
                    "$optionName.$operationReplacer(${versionOptionData.newOptionType}${convertedValue})"
                )
                return Replacement(binaryExpression, replacement.toString(), versionOptionData.fqClassName)
            }
        }
        // JS options
        else if (jsOptions.contains(optionName)) {
            val jsOptionsValuesStringToEnumCorrespondence = jsOptions[optionName] ?: return null
            val jsOptionValue = jsOptionsValuesStringToEnumCorrespondence[optionValue]
            if (jsOptionValue != null) {
                replacement.append(
                    "$optionName.$operationReplacer(${jsOptionValue.className}.${jsOptionValue.optionValue})"
                )
                return Replacement(binaryExpression, replacement.toString(), jsOptionValue.fqClassName)
            }
        } else if (optionName.contains("freeCompilerArgs")) {
            replacement.append("$optionName.$operationReplacer($textOfRightPartOfBinaryExpression)")
            return Replacement(binaryExpression, replacement.toString())
        }
    }
    // ALL OTHER
    return getReplacementOnlyOfKotlinOptionsIfNeeded(binaryExpression, textOfLeftPartOfBinaryExpression)
}

private fun getReplacementOnlyOfKotlinOptionsIfNeeded(
    binaryExpression: KtBinaryExpression,
    textOfLeftPartOfBinaryExpression: String
): Replacement? {
    if (textOfLeftPartOfBinaryExpression.startsWith("kotlinOptions.")) {
        val replacement = binaryExpression.text.replace("kotlinOptions.", "compilerOptions.")
        return Replacement(binaryExpression, replacement)
    }
    return null
}

private data class JsOptionValue(val optionValue: String, val className: String, val fqClassName: FqName)

/**
 * 1_8, 1_9
 * 1_10
 * 11, etc
 */
private val javaVersionRegex = Regex("(\\d_)?\\d(\\d)?")

/**
 * org.jetbrains.kotlin.gradle.dsl.JvmTarget class has values JVM_1_8, JVM_9, and all others higher without "1_".
 * org.gradle.api.JavaVersion class has values VERSION_1_1..VERSION_1_10, and then VERSION_11 and all others higher without "1_".
 *
 */
private fun jvmTargetValueMappingRule(inputValue: String): String? {
    // Parse ordinary String values like "1.8", "1.9", etc.
    if (inputValue == "1.8") return "1_8"
    val numericValue = inputValue.removePrefix("1.").toIntOrNull()
    if (numericValue != null) {
        if (numericValue <= 7) return null // JvmTarget class has values starting from 8
        else return numericValue.toString()
    }

    // parse JavaVersion.VERSION_N.toString()
    val version = javaVersionRegex.find(inputValue)?.value ?: return null
    when (version) {
        "1_8" -> return "1_8"
        else -> {
            val numericValue = version.removePrefix("1_").toIntOrNull() ?: return null
            if (numericValue > 8) {
                return numericValue.toString()
            } else { // Kotlin doesn't support jvmTarget 7 and less
                return null
            }
        }
    }
}

private val kotlinVersionRegex = Regex("\\d\\.\\d")

private fun kotlinVersionValueMappingRule(inputValue: String): String? {
    if (kotlinVersionRegex.matches(inputValue)) {
        return inputValue.replace(".", "_")
    } else return null
}

private data class VersionOption(val newOptionType: String, val fqClassName: FqName, val mappingRule: Function<String, String?>)

private val optionsWithValuesMigratedFromNumericStringsToEnums = mapOf(
    Pair(
        "jvmTarget",
        VersionOption("JvmTarget.JVM_", FqName("org.jetbrains.kotlin.gradle.dsl.JvmTarget"), ::jvmTargetValueMappingRule)
    ),
    Pair(
        "apiVersion",
        VersionOption("KotlinVersion.KOTLIN_", FqName("org.jetbrains.kotlin.gradle.dsl.KotlinVersion"), ::kotlinVersionValueMappingRule)
    ),
    Pair(
        "languageVersion",
        VersionOption("KotlinVersion.KOTLIN_", FqName("org.jetbrains.kotlin.gradle.dsl.KotlinVersion"), ::kotlinVersionValueMappingRule)
    )
)

private val sourceMapEmbedSourcesValues = mapOf(
    Pair(
        "inlining",
        JsOptionValue(
            "SOURCE_MAP_SOURCE_CONTENT_INLINING",
            "JsSourceMapEmbedMode",
            FqName("org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode")
        )
    ),
    Pair(
        "never",
        JsOptionValue(
            "SOURCE_MAP_SOURCE_CONTENT_NEVER",
            "JsSourceMapEmbedMode",
            FqName("org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode")
        )
    ),
    Pair(
        "always",
        JsOptionValue(
            "SOURCE_MAP_SOURCE_CONTENT_ALWAYS",
            "JsSourceMapEmbedMode",
            FqName("org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode")
        )
    )
)

private val jsMainFunctionExecutionModeValues = mapOf(
    Pair(
        "call",
        JsOptionValue("CALL", "JsMainFunctionExecutionMode", FqName("org.jetbrains.kotlin.gradle.dsl.JsMainFunctionExecutionMode"))
    ),
    Pair(
        "noCall",
        JsOptionValue("NO_CALL", "JsMainFunctionExecutionMode", FqName("org.jetbrains.kotlin.gradle.dsl.JsMainFunctionExecutionMode"))
    )
)

private val jsModuleKindValues = mapOf(
    Pair("amd", JsOptionValue("MODULE_AMD", "JsModuleKind", FqName("org.jetbrains.kotlin.gradle.dsl.JsModuleKind"))),
    Pair("plain", JsOptionValue("MODULE_PLAIN", "JsModuleKind", FqName("org.jetbrains.kotlin.gradle.dsl.JsModuleKind"))),
    Pair("es", JsOptionValue("MODULE_ES", "JsModuleKind", FqName("org.jetbrains.kotlin.gradle.dsl.JsModuleKind"))),
    Pair("commonjs", JsOptionValue("MODULE_COMMONJS", "JsModuleKind", FqName("org.jetbrains.kotlin.gradle.dsl.JsModuleKind"))),
    Pair("umd", JsOptionValue("MODULE_UMD", "JsModuleKind", FqName("org.jetbrains.kotlin.gradle.dsl.JsModuleKind")))
)

private val jsSourceMapNamesPolicyValues = mapOf(
    Pair(
        "fully-qualified-names",
        JsOptionValue(
            "SOURCE_MAP_NAMES_POLICY_FQ_NAMES",
            "JsSourceMapNamesPolicy",
            FqName("org.jetbrains.kotlin.gradle.dsl.JsSourceMapNamesPolicy")
        )
    ),
    Pair(
        "simple-names",
        JsOptionValue(
            "SOURCE_MAP_NAMES_POLICY_SIMPLE_NAMES",
            "JsSourceMapNamesPolicy",
            FqName("org.jetbrains.kotlin.gradle.dsl.JsSourceMapNamesPolicy")
        )
    ),
    Pair(
        "no",
        JsOptionValue(
            "SOURCE_MAP_NAMES_POLICY_NO",
            "JsSourceMapNamesPolicy",
            FqName("org.jetbrains.kotlin.gradle.dsl.JsSourceMapNamesPolicy")
        )
    ),
)

private val jsOptions = mapOf(
    Pair("main", jsMainFunctionExecutionModeValues),
    Pair("moduleKind", jsModuleKindValues),
    Pair("sourceMapEmbedSources", sourceMapEmbedSourcesValues),
    Pair("sourceMapNamesPolicy", jsSourceMapNamesPolicyValues)
)
