// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.utils

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.gradleJava.kotlinGradlePluginVersion
import org.jetbrains.kotlin.idea.gradleTooling.compareTo
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import java.util.function.Function

data class CompilerOption(val expression: String, val classToImport: FqName? = null, val compilerOptionValue: String? = null)

data class Replacement(val expressionToReplace: KtExpression, val replacement: String, val classToImport: FqName? = null)

@ApiStatus.Internal
fun expressionContainsOperationForbiddenToReplace(binaryExpression: KtBinaryExpression): Boolean {
    if (binaryExpression.operationToken == KtTokens.MINUSEQ) return true
    val rightPartOfBinaryExpression = binaryExpression.right ?: return true
    return if (rightPartOfBinaryExpression is KtBinaryExpression) {
        checkIfExpressionContainsMinusOperator(rightPartOfBinaryExpression)
    } else {
        false
    }
}

private fun checkIfExpressionContainsMinusOperator(binaryExpression: KtBinaryExpression): Boolean {
    if (binaryExpression.operationToken == KtTokens.MINUS) return true
    val leftPartOfBinaryExpression = binaryExpression.left ?: return true
    return if (leftPartOfBinaryExpression is KtBinaryExpression) {
        checkIfExpressionContainsMinusOperator(leftPartOfBinaryExpression)
    } else {
        false
    }
}

@ApiStatus.Internal
fun getReplacementForOldKotlinOptionIfNeeded(binaryExpression: KtBinaryExpression): Replacement? {

    val rightPartOfBinaryExpression = binaryExpression.right ?: return null

    val leftPartOfBinaryExpression = binaryExpression.left ?: return null

    val (optionName, replacementOfKotlinOptionsIfNeeded) = getOptionName(leftPartOfBinaryExpression)
        ?: return null

    if (rightPartOfBinaryExpression is KtBinaryExpression && !optionName.contains("freeCompilerArgs")) {
        return getReplacementOnlyOfKotlinOptionsIfNeeded(binaryExpression)
    }

    val (optionValue, valueContainsMultipleValues) = getOptionValue(rightPartOfBinaryExpression, optionName) ?: return null

    val operationToken = binaryExpression.operationToken

    val expressionForCompilerOption =
        getReplacementForOldKotlinOptionIfNeeded(
            replacementOfKotlinOptionsIfNeeded,
            optionName,
            optionValue,
            operationToken,
            valueContainsMultipleValues
        )
    if (expressionForCompilerOption != null) {
        return Replacement(binaryExpression, expressionForCompilerOption.expression, expressionForCompilerOption.classToImport)
    }
    // ALL OTHER
    return getReplacementOnlyOfKotlinOptionsIfNeeded(binaryExpression)
}

private fun getOptionValue(expression: KtExpression, optionName: String): Pair<String, Boolean>? {
    val optionValue: String
    val valueContainsMultipleValues: Boolean
    if (expression is KtBinaryExpression) {
        if (expression.operationToken != KtTokens.PLUS) {
            return null
        }
        if (optionName == "freeCompilerArgs") {
            val leftPart = expression.left ?: return null
            val rightPart = expression.right ?: return null
            val optionValues = getOptionsFromFreeCompilerArgsExpression(leftPart, mutableSetOf(rightPart.text)) ?: return null
            optionValue = StringUtil.join(optionValues.reversed(), ", ")
            valueContainsMultipleValues = true
        } else {
            return null
        }
    } else {
        optionValue = expression.text
        valueContainsMultipleValues = false
    }
    return Pair(optionValue, valueContainsMultipleValues)
}

/**
 * Returns collection of option values or null if something goes wrong.
 */
private fun getOptionsFromFreeCompilerArgsExpression(expression: KtExpression, optionValues: MutableSet<String>): Set<String>? {
    if (expression is KtBinaryExpression) {
        if (expression.operationToken != KtTokens.PLUS) return null
        optionValues.add(expression.right?.text ?: return null)
        getOptionsFromFreeCompilerArgsExpression(expression.left ?: return null, optionValues)
    } else {
        val expressionName = expression.text ?: return null
        if (expressionName != "freeCompilerArgs") {
            optionValues.add(expressionName)
        }
    }
    return optionValues
}

private fun getOptionName(expression: KtExpression): Pair<String, StringBuilder>? {
    val replacementOfKotlinOptionsIfNeeded = StringBuilder()
    val optionName = when (expression) {
        is KtDotQualifiedExpression -> {
            val partBeforeDot = expression.receiverExpression.text

            if (!partBeforeDot.contains("kotlinOptions")) {
                if (partBeforeDot != "options") {
                    return null
                }
            } else {
                replacementOfKotlinOptionsIfNeeded.append("compilerOptions.")
            }
            expression.getCalleeExpressionIfAny()?.text ?: return null
        }

        is KtNameReferenceExpression -> {
            expression.getReferencedName()
        }

        else -> {
            return null
        }
    }
    return Pair(optionName, replacementOfKotlinOptionsIfNeeded)
}

fun kotlinVersionIsEqualOrHigher(major: Int, minor: Int, patch: Int, file: PsiFile, kotlinVersion: IdeKotlinVersion? = null): Boolean {
    if (kotlinVersion != null) {
        val result = kotlinVersion.kotlinVersion >= KotlinVersion(
            major,
            minor,
            patch
        )
        return result
    }

    val version = file.module?.kotlinGradlePluginVersion ?: return false
    return version >= KotlinToolingVersion(major, minor, patch, classifier = null)
}

private fun getOperationReplacer(
    operationToken: IElementType,
    optionValue: String,
    valueContainsMultipleValues: Boolean = false
): String? {
    return when (operationToken) {
        KtTokens.EQ -> {
            if (valueContainsMultipleValues) {
                "addAll"
            } else {
                "set"
            }
        }

        KtTokens.PLUSEQ -> {
            if (!collectionsNamesRegex.find(optionValue)?.value.isNullOrEmpty() || valueContainsMultipleValues) {
                "addAll"
            } else {
                "add"
            }
        }

        else -> {
            null
        }
    }
}

private fun getReplacementForOldKotlinOptionIfNeeded(
    replacement: StringBuilder,
    optionName: String,
    optionValue: String,
    operationToken: IElementType,
    valueContainsMultipleValues: Boolean = false,
): CompilerOption? {
    val operationReplacer =
        getOperationReplacer(operationToken, optionValue, valueContainsMultipleValues) ?: return null
    // jvmTarget, apiVersion and languageVersion
    val versionOptionData = optionsWithValuesMigratedFromNumericStringsToEnums[optionName]
    if (versionOptionData != null) {
        return getCompilerOptionForVersionValue(
            versionOptionData,
            optionValue,
            replacement,
            optionName,
            operationReplacer
        )
    } else if (jsOptions.contains(optionName)) { // JS options
        val processedOptionValue = optionValue.removeSurrounding("\"").removeSurrounding("'")
        val jsOptionsValuesStringToEnumCorrespondence = jsOptions[optionName] ?: return null
        val jsOptionValue = jsOptionsValuesStringToEnumCorrespondence[processedOptionValue]
        if (jsOptionValue != null) {
            return getCompilerOptionForJsValue(jsOptionValue, replacement, optionName, operationReplacer)
        }
    } else {
        replacement.append("$optionName.$operationReplacer($optionValue)")
        return CompilerOption(replacement.toString())
    }
    return null
}

private fun getCompilerOptionForVersionValue(
    versionOptionData: VersionOption,
    optionValue: String,
    replacement: StringBuilder,
    optionName: String,
    operationReplacer: String,
): CompilerOption {
    val processedOptionValue = optionValue.removeSurrounding("\"").removeSurrounding("'")
    val convertedValue = versionOptionData.mappingRule.apply(processedOptionValue)
    val compilerOptionValue = if (convertedValue != null) {
        "${versionOptionData.newOptionType}${convertedValue}"
    } else if (optionName.contains("jvmTarget")) {
        "JvmTarget.fromTarget(${optionValue})"
    } else {
        "KotlinVersion.fromVersion(${optionValue})"
    }
    replacement.append(
        "$optionName.$operationReplacer($compilerOptionValue)"
    )
    return CompilerOption(replacement.toString(), versionOptionData.fqClassName, compilerOptionValue)
}

private fun getCompilerOptionForJsValue(
    jsOptionValue: JsOptionValue,
    replacement: StringBuilder,
    optionName: String,
    operationReplacer: String,
): CompilerOption {
    replacement.append(
        "$optionName.$operationReplacer(${jsOptionValue.className}.${jsOptionValue.optionValue})"
    )
    return CompilerOption(replacement.toString(), jsOptionValue.fqClassName)
}

@ApiStatus.Internal
fun getCompilerOption(optionName: String, optionValue: String): CompilerOption {
    val replacement = StringBuilder()
    val compilerOption = getReplacementForOldKotlinOptionIfNeeded(replacement, optionName, optionValue, operationToken = KtTokens.EQ)
    return compilerOption ?: CompilerOption("$optionName = $optionValue")
}

private val collectionsNamesRegex = Regex("listOf|mutableListOf|setOf|mutableSetOf")

private fun getReplacementOnlyOfKotlinOptionsIfNeeded(
    binaryExpression: KtBinaryExpression
): Replacement? {
    val leftPartOfBinaryExpression = binaryExpression.left ?: return null
    return if (leftPartOfBinaryExpression is KtDotQualifiedExpression && leftPartOfBinaryExpression.text.startsWith("kotlinOptions")) {
        val replacement = binaryExpression.text.replace("kotlinOptions", "compilerOptions")
        Replacement(binaryExpression, replacement)
    } else {
        null
    }
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
        return if (numericValue <= 7) {
            null // JvmTarget class has values starting from 8
        } else {
            numericValue.toString()
        }
    }

    // parse JavaVersion.VERSION_N.toString()
    val version = javaVersionRegex.find(inputValue)?.value ?: return null
    return when (version) {
        "1_8" -> "1_8"
        else -> {
            val numericValue = version.removePrefix("1_").toIntOrNull() ?: return null
            return if (numericValue > 8) {
                numericValue.toString()
            } else { // Kotlin doesn't support jvmTarget 7 and less
                null
            }
        }
    }
}

private val kotlinVersionRegex = Regex("\\d\\.\\d")

private fun kotlinVersionValueMappingRule(inputValue: String): String? {
    return if (kotlinVersionRegex.matches(inputValue)) {
        inputValue.replace(".", "_")
    } else {
        null
    }
}

private data class VersionOption(val newOptionType: String, val fqClassName: FqName, val mappingRule: Function<String, String?>)

private val kotlinVersionFqName = FqName("org.jetbrains.kotlin.gradle.dsl.KotlinVersion")
private val optionsWithValuesMigratedFromNumericStringsToEnums = mapOf(
    "jvmTarget" to VersionOption("JvmTarget.JVM_", FqName("org.jetbrains.kotlin.gradle.dsl.JvmTarget"), ::jvmTargetValueMappingRule),
    "apiVersion" to VersionOption("KotlinVersion.KOTLIN_", kotlinVersionFqName, ::kotlinVersionValueMappingRule),
    "languageVersion" to VersionOption("KotlinVersion.KOTLIN_", kotlinVersionFqName, ::kotlinVersionValueMappingRule)
)

private val jsSourceMapEmbedModeFqClassName = FqName("org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode")
private const val jsSourceMapEmbedModeClassName = "JsSourceMapEmbedMode"
private val sourceMapEmbedSourcesValues = mapOf(
    "inlining" to JsOptionValue("SOURCE_MAP_SOURCE_CONTENT_INLINING", jsSourceMapEmbedModeClassName, jsSourceMapEmbedModeFqClassName),
    "never" to JsOptionValue("SOURCE_MAP_SOURCE_CONTENT_NEVER", jsSourceMapEmbedModeClassName, jsSourceMapEmbedModeFqClassName),
    "always" to JsOptionValue("SOURCE_MAP_SOURCE_CONTENT_ALWAYS", jsSourceMapEmbedModeClassName, jsSourceMapEmbedModeFqClassName)
)

private val jsMainFunctionExecutionModeFqClassName = FqName("org.jetbrains.kotlin.gradle.dsl.JsMainFunctionExecutionMode")
private const val jsMainFunctionExecutionModeClassName = "JsMainFunctionExecutionMode"
private val jsMainFunctionExecutionModeValues = mapOf(
    "call" to JsOptionValue("CALL", jsMainFunctionExecutionModeClassName, jsMainFunctionExecutionModeFqClassName),
    "noCall" to JsOptionValue("NO_CALL", jsMainFunctionExecutionModeClassName, jsMainFunctionExecutionModeFqClassName)

)

private val jsModuleKindFqClassName = FqName("org.jetbrains.kotlin.gradle.dsl.JsModuleKind")
private const val jsModuleKindClassName = "JsModuleKind"
private val jsModuleKindValues = mapOf(
    "amd" to JsOptionValue("MODULE_AMD", jsModuleKindClassName, jsModuleKindFqClassName),
    "plain" to JsOptionValue("MODULE_PLAIN", jsModuleKindClassName, jsModuleKindFqClassName),
    "es" to JsOptionValue("MODULE_ES", jsModuleKindClassName, jsModuleKindFqClassName),
    "commonjs" to JsOptionValue("MODULE_COMMONJS", jsModuleKindClassName, jsModuleKindFqClassName),
    "umd" to JsOptionValue("MODULE_UMD", jsModuleKindClassName, jsModuleKindFqClassName)
)

private val jsSourceMapNamesPolicyFqClassName = FqName("org.jetbrains.kotlin.gradle.dsl.JsSourceMapNamesPolicy")
private const val jsSourceMapNamesPolicyClassName = "JsSourceMapNamesPolicy"
private val jsSourceMapNamesPolicyValues = mapOf(
    "fully-qualified-names" to
            JsOptionValue("SOURCE_MAP_NAMES_POLICY_FQ_NAMES", jsSourceMapNamesPolicyClassName, jsSourceMapNamesPolicyFqClassName),
    "simple-names" to
            JsOptionValue("SOURCE_MAP_NAMES_POLICY_SIMPLE_NAMES", jsSourceMapNamesPolicyClassName, jsSourceMapNamesPolicyFqClassName),
    "no" to JsOptionValue("SOURCE_MAP_NAMES_POLICY_NO", jsSourceMapNamesPolicyClassName, jsSourceMapNamesPolicyFqClassName),
)

private val jsOptions = mapOf(
    "main" to jsMainFunctionExecutionModeValues,
    "moduleKind" to jsModuleKindValues,
    "sourceMapEmbedSources" to sourceMapEmbedSourcesValues,
    "sourceMapNamesPolicy" to jsSourceMapNamesPolicyValues
)
