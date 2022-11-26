// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import org.jetbrains.plugins.gradle.importing.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.testFramework.util.buildSettings
import org.junit.jupiter.api.Assertions.assertEquals

abstract class GradleSettingsScriptBuilderTestCase {

  fun assertBuildSettings(
    groovyScript: String,
    kotlinScript: String,
    configure: GradleSettingScriptBuilder.() -> Unit
  ) {
    assertEquals(groovyScript, buildSettings(useKotlinDsl = false, configure))
    assertEquals(kotlinScript, buildSettings(useKotlinDsl = true, configure))
  }
}