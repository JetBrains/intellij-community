// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.application.PathManager
import com.intellij.platform.pluginManager.testFramework.PluginManagerSpecVerifier
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.Timeout
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class PluginManagerSpecReferencesTest {
  @TestFactory
  fun `plugin manager specifications have valid references`(): List<DynamicTest> {
    val communityRoot = Path.of(PathManager.getCommunityHomePath())
    val platformImpl = communityRoot.resolve("platform/platform-impl")
    val violations = PluginManagerSpecVerifier(
      repositoryRoot = communityRoot,
      specRoot = platformImpl.resolve("spec/plugin-manager"),
      scanRoots = listOf(
        platformImpl.resolve("src/com/intellij/ide/plugins"),
        platformImpl.resolve("testSrc/com/intellij/ide/plugins"),
      ),
      sectionRequirements = mapOf(
        "unified-plugin-manager-ui.spec.md" to setOf(
          "Purpose",
          "Scope",
          "Page Structure",
          "Section Membership",
          "Search and Navigation",
          "Search Reporting",
          "Source Loading",
          "Page Interaction",
          "Presentation",
          "Product Integration",
          "Source Model",
          "Failure and Recovery",
          "Verification",
          "Open Questions",
        ),
        "plugin-operations.spec.md" to setOf(
          "Purpose",
          "Scope",
          "Operation Lifecycle",
          "Installing Section",
          "Settings Session",
          "Install and Update",
          "Management Actions",
          "Update All",
          "Operation Presentation",
          "Operation Integration",
          "Failure and Recovery",
          "Verification",
          "Open Questions",
        ),
      ),
    ).validate()

    return violations.asDynamicTests()
  }
}

private fun List<String>.asDynamicTests(): List<DynamicTest> {
  if (isEmpty()) return listOf(dynamicTest("all specification references are valid") {})
  return map { violation -> dynamicTest(violation) { throw AssertionError(violation) } }
}
