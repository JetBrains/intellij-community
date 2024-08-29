// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.utils

import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny
import java.util.function.Function

data class CompilerOption(val expression: String, val classToImport: FqName? = null, val compilerOptionValue: String? = null)

data class Replacement(val expressionToReplace: KtExpression, val replacement: String, val classToImport: FqName? = null)

fun getReplacementForOldKotlinOptionIfNeeded(binaryExpression: KtBinaryExpression): Replacement? {

  val rightPartOfBinaryExpression = binaryExpression.right ?: return null
  val textOfRightPartOfBinaryExpression = rightPartOfBinaryExpression.text

  val leftPartOfBinaryExpression = binaryExpression.left ?: return null
  val textOfLeftPartOfBinaryExpression = leftPartOfBinaryExpression.text

  // We don't touch strings like `freeCompilerArgs = freeCompilerArgs + "-Xopt-in=kotlin.RequiresOptIn"`
  if (rightPartOfBinaryExpression is KtBinaryExpression) {
    return getReplacementOnlyOfKotlinOptionsIfNeeded(binaryExpression, textOfLeftPartOfBinaryExpression)
  }

  val replacement = StringBuilder()
  val optionName = when (leftPartOfBinaryExpression) {
    is KtDotQualifiedExpression -> {
      val partBeforeDot = leftPartOfBinaryExpression.receiverExpression.text
      if (partBeforeDot != "kotlinOptions") {
        return null
      }
      else {
        replacement.append("compilerOptions.")
      }
      leftPartOfBinaryExpression.getCalleeExpressionIfAny()?.text ?: return null
    }

    is KtReferenceExpression -> {
      textOfLeftPartOfBinaryExpression
    }

    else -> {
      return null
    }
  }

  val operationReference = binaryExpression.operationReference.text

  val expressionForCompilerOption =
    getReplacementForOldKotlinOptionIfNeeded(replacement, optionName, textOfRightPartOfBinaryExpression, operationReference)
  if (expressionForCompilerOption != null) {
    return Replacement(binaryExpression, expressionForCompilerOption.expression, expressionForCompilerOption.classToImport)
  }
  // ALL OTHER
  return getReplacementOnlyOfKotlinOptionsIfNeeded(binaryExpression, textOfLeftPartOfBinaryExpression)
}

fun kotlinVersionIsEqualOrHigher(major: Int, minor: Int, patch: Int, file: PsiFile): Boolean {
  val jpsVersion = KotlinJpsPluginSettings.jpsVersion(file.project)
  val parsedKotlinVersion = IdeKotlinVersion.opt(jpsVersion)?.kotlinVersion ?: return false
  return parsedKotlinVersion >= KotlinVersion(major, minor, patch)
}

private fun getOperationReplacer(operationReference: String, optionValue: String): String? {
  return when (operationReference) {
    "=" -> {
      "set"
    }

    "+=" -> {
      if (!collectionsNamesRegex.find(optionValue)?.value.isNullOrEmpty()) {
        "addAll"
      }
      else {
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
  operationReference: String,
): CompilerOption? {

  val operationReplacer = getOperationReplacer(operationReference, optionValue)

  if (operationReplacer != null) {
    // jvmTarget, apiVersion and languageVersion
    val versionOptionData = optionsWithValuesMigratedFromNumericStringsToEnums[optionName]
    if (versionOptionData != null) {

      return getCompilerOptionForVersionValue(versionOptionData, optionValue, replacement, optionName, operationReplacer)

    }
    else if (jsOptions.contains(optionName)) { // JS options
      val processedOptionValue = optionValue.removeSurrounding("\"", "\"")
      val jsOptionsValuesStringToEnumCorrespondence = jsOptions[optionName] ?: return null
      val jsOptionValue = jsOptionsValuesStringToEnumCorrespondence[processedOptionValue]
      if (jsOptionValue != null) {
        return getCompilerOptionForJsValue(jsOptionValue, replacement, optionName, operationReplacer)
      }

    }
    else {
      replacement.append("$optionName.$operationReplacer($optionValue)")
      return CompilerOption(replacement.toString())
    }
  }
  return null
}

private fun getCompilerOptionForVersionValue(
  versionOptionData: VersionOption, optionValue: String, replacement: StringBuilder,
  optionName: String, operationReplacer: String,
): CompilerOption? {
  val processedOptionValue = optionValue.removeSurrounding("\"", "\"")
  val convertedValue = versionOptionData.mappingRule.apply(processedOptionValue)
  if (convertedValue != null) {
    val compilerOptionValue = "${versionOptionData.newOptionType}${convertedValue}"
    replacement.append(
      "$optionName.$operationReplacer($compilerOptionValue)"
    )
    return CompilerOption(replacement.toString(), versionOptionData.fqClassName, compilerOptionValue)
  }
  return null
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

fun getCompilerOption(optionName: String, optionValue: String): CompilerOption {
  val replacement = StringBuilder()
  val compilerOption = getReplacementForOldKotlinOptionIfNeeded(replacement, optionName, optionValue, operationReference = "=")
  return compilerOption ?: CompilerOption("$optionName = $optionValue")
}

private val collectionsNamesRegex = Regex("listOf|mutableListOf|setOf|mutableSetOf")

private fun getReplacementOnlyOfKotlinOptionsIfNeeded(
  binaryExpression: KtBinaryExpression,
  textOfLeftPartOfBinaryExpression: String,
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
      }
      else { // Kotlin doesn't support jvmTarget 7 and less
        return null
      }
    }
  }
}

private val kotlinVersionRegex = Regex("\\d\\.\\d")

private fun kotlinVersionValueMappingRule(inputValue: String): String? {
  if (kotlinVersionRegex.matches(inputValue)) {
    return inputValue.replace(".", "_")
  }
  else return null
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
