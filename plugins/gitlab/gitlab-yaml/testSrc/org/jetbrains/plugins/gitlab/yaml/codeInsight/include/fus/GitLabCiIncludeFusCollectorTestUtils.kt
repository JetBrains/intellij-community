// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.yaml.codeInsight.include.fus;

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

internal fun assertQuality(
  actualResult: AnalysisResult,
  filesAnalyzed: Int,
  filesFailed: Int,
  timeoutHappened: Boolean,
) {
  assertEquals(filesAnalyzed, actualResult.filesAnalyzed, "Files analyzed count mismatch")
  assertEquals(filesFailed, actualResult.filesFailed, "Files failed count mismatch")
  assertEquals(timeoutHappened, actualResult.timeoutHappened, "Timeout happened mismatch")
}

internal fun assertExplicitLocalFound(
  actualStats: IncludeStats,
  hasRules: Boolean,
  hasEnvVar: Boolean,
  hasSingleAsterisk: Boolean,
  hasDoubleAsterisk: Boolean,
) {
  assertIncludeType(actualStats.explicitLocal,
                    "Explicit local",
                    true,
                    hasRules,
                    hasEnvVar,
                    hasSingleAsterisk,
                    hasDoubleAsterisk,
                    false,
                    false)
}

internal fun assertExplicitLocalNotFound(actualStats: IncludeStats) {
  assertIncludeType(actualStats.explicitLocal, "Explicit local", false, false, false, false, false, false, false)
}

internal fun assertExplicitRemoteFound(
  actualStats: IncludeStats,
  hasRules: Boolean,
  hasEnvVar: Boolean,
  hasCache: Boolean,
) {
  assertIncludeType(actualStats.explicitRemote, "Explicit remote", true, hasRules, hasEnvVar, false, false, hasCache, false)
}

internal fun assertExplicitRemoteNotFound(actualStats: IncludeStats) {
  assertIncludeType(actualStats.explicitRemote, "Explicit remote", false, false, false, false, false, false, false)
}

internal fun assertImplicitLocalOrRemoteFound(
  actualStats: IncludeStats,
  hasEnvVar: Boolean,
  hasSingleAsterisk: Boolean,
  hasDoubleAsterisk: Boolean,
) {
  assertIncludeType(actualStats.implicitLocalOrRemote,
                    "Implicit local or remote",
                    true,
                    false,
                    hasEnvVar,
                    hasSingleAsterisk,
                    hasDoubleAsterisk,
                    false,
                    false)
}

internal fun assertImplicitLocalOrRemoteNotFound(actualStats: IncludeStats) {
  assertIncludeType(actualStats.implicitLocalOrRemote, "Implicit local or remote", false, false, false, false, false, false, false)
}

internal fun assertTemplateFound(
  actualStats: IncludeStats,
  hasRules: Boolean,
  hasEnvVar: Boolean,
) {
  assertIncludeType(actualStats.template, "Template", true, hasRules, hasEnvVar, false, false, false, false)
}

internal fun assertTemplateNotFound(actualStats: IncludeStats) {
  assertIncludeType(actualStats.template, "Template", false, false, false, false, false, false, false)
}

internal fun assertComponentFound(
  actualStats: IncludeStats,
  hasRules: Boolean,
  hasEnvVar: Boolean,
) {
  assertIncludeType(actualStats.component, "Component", true, hasRules, hasEnvVar, false, false, false, false)
}

internal fun assertComponentNotFound(actualStats: IncludeStats) {
  assertIncludeType(actualStats.component, "Component", false, false, false, false, false, false, false)
}

internal fun assertProjectFound(
  actualStats: IncludeStats,
  hasRules: Boolean,
  hasEnvVar: Boolean,
  hasRef: Boolean,
  hasSingleAsterisk: Boolean,
  hasDoubleAsterisk: Boolean,
) {
  assertIncludeType(actualStats.project, "Project", true, hasRules, hasEnvVar, hasSingleAsterisk, hasDoubleAsterisk, false, hasRef)
}

internal fun assertProjectNotFound(actualStats: IncludeStats) {
  assertIncludeType(actualStats.project, "Project", false, false, false, false, false, false, false)
}

internal fun assertUnknownFound(actualStats: IncludeStats) {
  assertTrue(actualStats.unknown, "Unknown should be found")
}

internal fun assertUnknownNotFound(actualStats: IncludeStats) {
  assertFalse(actualStats.unknown, "Unknown should not be found")
}

private fun assertIncludeType(
  actualTypeStats: IncludeTypeStats,
  typeName: String,
  found: Boolean,
  hasRules: Boolean,
  hasEnvVar: Boolean,
  hasSingleAsterisk: Boolean,
  hasDoubleAsterisk: Boolean,
  hasCache: Boolean,
  hasRef: Boolean,
) {
  if (found) assertTrue(actualTypeStats.found, "$typeName should be found")
  else assertFalse(actualTypeStats.found, "$typeName should not be found")

  if (hasRules) assertTrue(actualTypeStats.hasRules, "$typeName should have rules")
  else assertFalse(actualTypeStats.hasRules, "$typeName should not have rules")

  if (hasEnvVar) assertTrue(actualTypeStats.hasEnvVar, "$typeName should have env var")
  else assertFalse(actualTypeStats.hasEnvVar, "$typeName should not have env var")

  if (hasSingleAsterisk) assertTrue(actualTypeStats.hasSingleAsterisk, "$typeName should have single asterisk")
  else assertFalse(actualTypeStats.hasSingleAsterisk, "$typeName should not have single asterisk")

  if (hasDoubleAsterisk) assertTrue(actualTypeStats.hasDoubleAsterisk, "$typeName should have double asterisk")
  else assertFalse(actualTypeStats.hasDoubleAsterisk, "$typeName should not have double asterisk")

  if (hasCache) assertTrue(actualTypeStats.hasCache, "$typeName should have cache")
  else assertFalse(actualTypeStats.hasCache, "$typeName should not have cache")

  if (hasRef) assertTrue(actualTypeStats.hasRef, "$typeName should have ref")
  else assertFalse(actualTypeStats.hasRef, "$typeName should not have ref")
}