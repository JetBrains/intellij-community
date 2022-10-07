// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import org.junit.jupiter.api.Test

class GradleSettingsScriptBuilderTest : GradleSettingsScriptBuilderTestCase() {

  @Test
  fun `test empty script`() {
    assertBuildSettings("", "") {}
  }

  @Test
  fun `test module configuration`() {
    assertBuildSettings("""
      rootProject.name = 'project'
      include 'module'
      includeFlat 'flat-module'
      includeBuild '../composite'
    """.trimIndent(), """
      rootProject.name = "project"
      include("module")
      includeFlat("flat-module")
      includeBuild("../composite")
    """.trimIndent()) {
      setProjectName("project")
      include("module")
      includeFlat("flat-module")
      includeBuild("../composite")
    }
  }
}