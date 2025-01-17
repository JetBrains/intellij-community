// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import org.junit.jupiter.api.Test
import java.nio.file.Path

class GradleSettingsScriptBuilderTest : GradleSettingsScriptBuilderTestCase() {

  @Test
  fun `test empty script`() {
    assertBuildSettings("", "") {}
  }

  @Test
  fun `test module configuration directly`() {
    assertBuildSettings("""
      |rootProject.name = 'project'
      |include 'module'
      |includeFlat 'flat-module'
      |includeBuild '../composite'
    """.trimMargin(), """
      |rootProject.name = "project"
      |include("module")
      |includeFlat("flat-module")
      |includeBuild("../composite")
    """.trimMargin()) {
      setProjectName("project")
      include("module")
      includeFlat("flat-module")
      includeBuild("../composite")
    }
  }

  @Test
  fun `test module configuration by path`() {
    assertBuildSettings("""
      |include 'module'
      |include 'module:sub-module'
      |includeFlat 'flat-project'
      |include 'flat-project:module'
      |project(':flat-project:module').projectDir = file('../flat-project/module')
      |include 'flat-project:module:sub-module'
      |project(':flat-project:module:sub-module').projectDir = file('../flat-project/module/sub-module')
      |include 'external-project'
      |project(':external-project').projectDir = file('../../external-project')
      |include 'external-project:module'
      |project(':external-project:module').projectDir = file('../../external-project/module')
      |include 'external-project:module:sub-module'
      |project(':external-project:module:sub-module').projectDir = file('../../external-project/module/sub-module')
    """.trimMargin(), """
      |include("module")
      |include("module:sub-module")
      |includeFlat("flat-project")
      |include("flat-project:module")
      |project(":flat-project:module").projectDir = file("../flat-project/module")
      |include("flat-project:module:sub-module")
      |project(":flat-project:module:sub-module").projectDir = file("../flat-project/module/sub-module")
      |include("external-project")
      |project(":external-project").projectDir = file("../../external-project")
      |include("external-project:module")
      |project(":external-project:module").projectDir = file("../../external-project/module")
      |include("external-project:module:sub-module")
      |project(":external-project:module:sub-module").projectDir = file("../../external-project/module/sub-module")
    """.trimMargin()) {
      include(Path.of("module"))
      include(Path.of("module/sub-module"))
      include(Path.of("../flat-project"))
      include(Path.of("../flat-project/module"))
      include(Path.of("../flat-project/module/sub-module"))
      include(Path.of("../../external-project"))
      include(Path.of("../../external-project/module"))
      include(Path.of("../../external-project/module/sub-module"))
    }
  }

  @Test
  fun `test foojay plugin configuration`() {
    assertBuildSettings("""
      |plugins {
      |    id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
      |}
    """.trimMargin(), """
      |plugins {
      |    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
      |}
    """.trimMargin()) {
      withFoojayPlugin()
    }
  }
}