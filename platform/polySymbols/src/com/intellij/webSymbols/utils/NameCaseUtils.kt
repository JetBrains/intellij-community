// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.utils

import org.jetbrains.annotations.ApiStatus

@Deprecated("Use com.intellij.polySymbols.utils.NameCaseUtils instead")
@ApiStatus.ScheduledForRemoval
@ApiStatus.Experimental
object NameCaseUtils {
  @JvmStatic
  @Deprecated("Use com.intellij.polySymbols.utils.NameCaseUtils.toPascalCase instead",
              replaceWith = ReplaceWith("com.intellij.polySymbols.utils.NameCaseUtils.toPascalCase(str)"))
  fun toPascalCase(str: String): String =
    com.intellij.polySymbols.utils.NameCaseUtils.toPascalCase(str)

  @JvmStatic
  @Deprecated("Use com.intellij.polySymbols.utils.NameCaseUtils.toPascalCase instead",
              replaceWith = ReplaceWith("com.intellij.polySymbols.utils.NameCaseUtils.toPascalCase(str, preserveConsecutiveUppercase)"))
  fun toPascalCase(str: String, preserveConsecutiveUppercase: Boolean): String =
    com.intellij.polySymbols.utils.NameCaseUtils.toPascalCase(str, preserveConsecutiveUppercase)

  @JvmStatic
  @Deprecated("Use com.intellij.polySymbols.utils.NameCaseUtils.toCamelCase instead",
              replaceWith = ReplaceWith("com.intellij.polySymbols.utils.NameCaseUtils.toCamelCase(str)"))
  fun toCamelCase(str: String): String =
    com.intellij.polySymbols.utils.NameCaseUtils.toCamelCase(str)

  @JvmStatic
  @Deprecated("Use com.intellij.polySymbols.utils.NameCaseUtils.toCamelCase instead",
              replaceWith = ReplaceWith("com.intellij.polySymbols.utils.NameCaseUtils.toCamelCase(str, preserveConsecutiveUppercase)"))
  fun toCamelCase(str: String, preserveConsecutiveUppercase: Boolean): String =
    com.intellij.polySymbols.utils.NameCaseUtils.toCamelCase(str, preserveConsecutiveUppercase)

  @JvmStatic
  @Deprecated("Use com.intellij.polySymbols.utils.NameCaseUtils.toKebabCase instead",
              replaceWith = ReplaceWith("com.intellij.polySymbols.utils.NameCaseUtils.toKebabCase(str)"))
  fun toKebabCase(str: String): String =
    com.intellij.polySymbols.utils.NameCaseUtils.toKebabCase(str)

  @JvmStatic
  @Deprecated("Use com.intellij.polySymbols.utils.NameCaseUtils.toKebabCase instead",
              replaceWith = ReplaceWith("com.intellij.polySymbols.utils.NameCaseUtils.toKebabCase(str, noHyphenBeforeDigit, noHyphenBetweenDigitAndLowercase, splitConsecutiveUppercase)"))
  fun toKebabCase(
    str: String,
    noHyphenBeforeDigit: Boolean,
    noHyphenBetweenDigitAndLowercase: Boolean,
    splitConsecutiveUppercase: Boolean,
  ): String =
    com.intellij.polySymbols.utils.NameCaseUtils.toKebabCase(str, noHyphenBeforeDigit, noHyphenBetweenDigitAndLowercase, splitConsecutiveUppercase)

  @JvmStatic
  @Deprecated("Use com.intellij.polySymbols.utils.NameCaseUtils.toSnakeCase instead",
              replaceWith = ReplaceWith("com.intellij.polySymbols.utils.NameCaseUtils.toSnakeCase(str)"))
  fun toSnakeCase(str: String): String =
    com.intellij.polySymbols.utils.NameCaseUtils.toSnakeCase(str)

  @JvmStatic
  @Deprecated("Use com.intellij.polySymbols.utils.NameCaseUtils.toSnakeCase instead",
              replaceWith = ReplaceWith("com.intellij.polySymbols.utils.NameCaseUtils.toSnakeCase(str, noUnderscoreBeforeDigit, noUnderscoreBetweenDigitAndLowercase, splitConsecutiveUppercase)"))
  fun toSnakeCase(
    str: String,
    noUnderscoreBeforeDigit: Boolean,
    noUnderscoreBetweenDigitAndLowercase: Boolean,
    splitConsecutiveUppercase: Boolean,
  ): String =
    com.intellij.polySymbols.utils.NameCaseUtils.toSnakeCase(str, noUnderscoreBeforeDigit, noUnderscoreBetweenDigitAndLowercase, splitConsecutiveUppercase)


}