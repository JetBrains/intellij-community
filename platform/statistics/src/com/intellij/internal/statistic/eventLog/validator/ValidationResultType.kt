// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.validator

import org.jetbrains.annotations.NonNls

enum class ValidationResultType(
  @field:NonNls val description: String,
  /**
   * Indicates if we should return the result or continue iterating through rules list,
   *
   * e.g. we want to check other rules if resultType==UNDEFINED_RULE
   * but we want to stop iterating if resultType==ACCEPTED
   */
  val isFinal: Boolean,
) {
  ACCEPTED("accepted", true),
  THIRD_PARTY("third.party", false),
  REJECTED("validation.unmatched_rule", false),
  INCORRECT_RULE("validation.incorrect_rule", false),
  UNDEFINED_RULE("validation.undefined_rule", false),
  UNREACHABLE_METADATA("validation.unreachable_metadata", true),
  DICTIONARY_NOT_FOUND("validation.dictionary_not_found", true),
  GENERAL_DICTIONARY_ERROR("validation.general_dictionary_error", true),

  // renamed into UNREACHABLE_METADATA, needed for correct server validation
  @Deprecated("")
  UNREACHABLE_METADATA_OBSOLETE("validation.unreachable.whitelist", true),
  PERFORMANCE_ISSUE("validation.performance_issue", true),
  REQUIRED_FIELD_MISSED("validation.required_field_missed", true),
  DEFAULT_VALUE_APPLIED("validation.default_value_applied", true);

  companion object {
    @JvmField
    val VALIDATION_TYPES: Set<String> = values().map { obj: ValidationResultType -> obj.description }.toSet()

    @JvmStatic
    fun toFusApiResultType(
      resultType: ValidationResultType,
    ): com.jetbrains.fus.reporting.api.ValidationResultType =
      when (resultType) {
        ACCEPTED -> com.jetbrains.fus.reporting.api.ValidationResultType.ACCEPTED
        THIRD_PARTY -> com.jetbrains.fus.reporting.api.ValidationResultType.THIRD_PARTY
        INCORRECT_RULE -> com.jetbrains.fus.reporting.api.ValidationResultType.INCORRECT_RULE
        UNDEFINED_RULE -> com.jetbrains.fus.reporting.api.ValidationResultType.UNDEFINED_RULE
        UNREACHABLE_METADATA -> com.jetbrains.fus.reporting.api.ValidationResultType.UNREACHABLE_METADATA
        DICTIONARY_NOT_FOUND -> com.jetbrains.fus.reporting.api.ValidationResultType.DICTIONARY_NOT_FOUND
        GENERAL_DICTIONARY_ERROR -> com.jetbrains.fus.reporting.api.ValidationResultType.GENERAL_DICTIONARY_ERROR
        UNREACHABLE_METADATA_OBSOLETE -> com.jetbrains.fus.reporting.api.ValidationResultType.UNREACHABLE_METADATA_OBSOLETE
        PERFORMANCE_ISSUE -> com.jetbrains.fus.reporting.api.ValidationResultType.PERFORMANCE_ISSUE
        REQUIRED_FIELD_MISSED -> com.jetbrains.fus.reporting.api.ValidationResultType.REQUIRED_FIELD_MISSED
        DEFAULT_VALUE_APPLIED -> com.jetbrains.fus.reporting.api.ValidationResultType.DEFAULT_VALUE_APPLIED
        REJECTED -> com.jetbrains.fus.reporting.api.ValidationResultType.REJECTED
      }

    @JvmStatic
    fun fromFusApiResultType(
      resultType: com.jetbrains.fus.reporting.api.ValidationResultType,
    ): ValidationResultType =
      when (resultType) {
        com.jetbrains.fus.reporting.api.ValidationResultType.ACCEPTED -> ACCEPTED
        com.jetbrains.fus.reporting.api.ValidationResultType.THIRD_PARTY -> THIRD_PARTY
        com.jetbrains.fus.reporting.api.ValidationResultType.INCORRECT_RULE -> INCORRECT_RULE
        com.jetbrains.fus.reporting.api.ValidationResultType.UNDEFINED_RULE -> UNDEFINED_RULE
        com.jetbrains.fus.reporting.api.ValidationResultType.UNREACHABLE_METADATA -> UNREACHABLE_METADATA
        com.jetbrains.fus.reporting.api.ValidationResultType.DICTIONARY_NOT_FOUND -> DICTIONARY_NOT_FOUND
        com.jetbrains.fus.reporting.api.ValidationResultType.GENERAL_DICTIONARY_ERROR -> GENERAL_DICTIONARY_ERROR
        com.jetbrains.fus.reporting.api.ValidationResultType.UNREACHABLE_METADATA_OBSOLETE -> UNREACHABLE_METADATA_OBSOLETE
        com.jetbrains.fus.reporting.api.ValidationResultType.PERFORMANCE_ISSUE -> PERFORMANCE_ISSUE
        com.jetbrains.fus.reporting.api.ValidationResultType.REQUIRED_FIELD_MISSED -> REQUIRED_FIELD_MISSED
        com.jetbrains.fus.reporting.api.ValidationResultType.DEFAULT_VALUE_APPLIED -> DEFAULT_VALUE_APPLIED
        com.jetbrains.fus.reporting.api.ValidationResultType.REJECTED -> REJECTED
      }
  }
}