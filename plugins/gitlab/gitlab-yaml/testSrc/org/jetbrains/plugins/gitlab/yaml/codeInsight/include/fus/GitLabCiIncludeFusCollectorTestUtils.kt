// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.yaml.codeInsight.include.fus;

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

internal fun AnalyzingResult.assertQuality(
  filesAnalyzed: Int,
  filesFailed: Int,
  timeoutHappened: Boolean
) {
  assertEquals(filesAnalyzed, this.filesAnalyzed, "Files analyzed count mismatch")
  assertEquals(filesFailed, this.filesFailed, "Files failed count mismatch")
  assertEquals(timeoutHappened, this.timeoutHappened, "Timeout happened mismatch")
}

internal fun IncludeStats.assertExplicitLocalFound(
  hasRules: Boolean,
  hasEnvVar: Boolean,
  hasSingleAsterisk: Boolean,
  hasDoubleAsterisk: Boolean,
  ) {
  this.explicitLocal.assertIncludeType("Explicit local", true, hasRules, hasEnvVar, hasSingleAsterisk, hasDoubleAsterisk, false, false)
}

internal fun IncludeStats.assertExplicitLocalNotFound() {
  this.explicitLocal.assertIncludeType("Explicit local", false, false, false, false, false, false, false)
}

internal fun IncludeStats.assertExplicitRemoteFound(
  hasRules: Boolean,
  hasEnvVar: Boolean,
  hasCache: Boolean,
  ) {
  this.explicitRemote.assertIncludeType("Explicit remote", true, hasRules, hasEnvVar, false, false, hasCache, false)
}

internal fun IncludeStats.assertExplicitRemoteNotFound() {
  this.explicitRemote.assertIncludeType("Explicit remote", false, false, false, false, false, false, false)
}

internal fun IncludeStats.assertImplicitLocalOrRemoteFound(
  hasEnvVar: Boolean,
  hasSingleAsterisk: Boolean,
  hasDoubleAsterisk: Boolean,
  ) {
  this.implicitLocalOrRemote.assertIncludeType("Implicit local or remote", true, false, hasEnvVar, hasSingleAsterisk, hasDoubleAsterisk, false, false)
}

internal fun IncludeStats.assertImplicitLocalOrRemoteNotFound() {
  this.implicitLocalOrRemote.assertIncludeType("Implicit local or remote", false, false, false, false, false, false, false)
}

internal fun IncludeStats.assertTemplateFound(
  hasRules: Boolean,
  hasEnvVar: Boolean,
  ) {
  this.template.assertIncludeType("Template", true, hasRules, hasEnvVar, false, false, false, false)
}

internal fun IncludeStats.assertTemplateNotFound() {
  this.template.assertIncludeType("Template", false, false, false, false, false, false, false)
}

internal fun IncludeStats.assertComponentFound(
  hasRules: Boolean,
  hasEnvVar: Boolean,
  ) {
  this.component.assertIncludeType("Component", true, hasRules, hasEnvVar, false, false, false, false)
}

internal fun IncludeStats.assertComponentNotFound() {
  this.component.assertIncludeType("Component", false, false, false, false, false, false, false)
}

internal fun IncludeStats.assertProjectFound(
  hasRules: Boolean,
  hasEnvVar: Boolean,
  hasRef: Boolean,
  hasSingleAsterisk: Boolean,
  hasDoubleAsterisk: Boolean,
  ) {
  this.project.assertIncludeType("Project", true, hasRules, hasEnvVar, hasSingleAsterisk, hasDoubleAsterisk, false, hasRef)
}

internal fun IncludeStats.assertProjectNotFound() {
  this.project.assertIncludeType("Project", false, false, false, false, false, false, false)
}

internal fun IncludeStats.assertUnknownFound() {
  assertTrue(unknown, "Unknown should be found")
}

internal fun IncludeStats.assertUnknownNotFound() {
  assertFalse(unknown, "Unknown should not be found")
}

private fun IncludeTypeStats.assertIncludeType(
  typeName: String,
  found: Boolean,
  hasRules: Boolean,
  hasEnvVar: Boolean,
  hasSingleAsterisk: Boolean,
  hasDoubleAsterisk: Boolean,
  hasCache: Boolean,
  hasRef: Boolean,
  ) {
  if (found) assertTrue(this.found, "$typeName should be found")
  else assertFalse(this.found, "$typeName should not be found")

  if (hasRules) assertTrue(this.hasRules, "$typeName should have rules")
  else assertFalse(this.hasRules, "$typeName should not have rules")

  if (hasEnvVar) assertTrue(this.hasEnvVar, "$typeName should have env var")
  else assertFalse(this.hasEnvVar, "$typeName should not have env var")

  if (hasSingleAsterisk) assertTrue(this.hasSingleAsterisk, "$typeName should have single asterisk")
  else assertFalse(this.hasSingleAsterisk, "$typeName should not have single asterisk")

  if (hasDoubleAsterisk) assertTrue(this.hasDoubleAsterisk, "$typeName should have double asterisk")
  else assertFalse(this.hasDoubleAsterisk, "$typeName should not have double asterisk")

  if (hasCache) assertTrue(this.hasCache, "$typeName should have cache")
  else assertFalse(this.hasCache, "$typeName should not have cache")

  if (hasRef) assertTrue(this.hasRef, "$typeName should have ref")
  else assertFalse(this.hasRef, "$typeName should not have ref")
}