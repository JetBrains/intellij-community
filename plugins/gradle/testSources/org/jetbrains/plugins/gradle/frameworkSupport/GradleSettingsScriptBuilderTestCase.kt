// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.junit.jupiter.api.Assertions

abstract class GradleSettingsScriptBuilderTestCase {

  fun assertBuildSettings(
    vararg cases: Pair<GradleVersion, Pair<String, String>>,
    configure: GradleSettingScriptBuilder<*>.() -> Unit
  ) {
    for ((gradleVersion, expectedScripts) in cases) {
      val (expectedGroovyScript, expectedKotlinScript) = expectedScripts
      val actualGroovyScript = GradleSettingScriptBuilder.create(gradleVersion, useKotlinDsl = false).apply(configure).generate()
      val actualKotlinScript = GradleSettingScriptBuilder.create(gradleVersion, useKotlinDsl = true).apply(configure).generate()
      Assertions.assertEquals(expectedGroovyScript, actualGroovyScript) {
        "Groovy scripts should be equal"
      }
      Assertions.assertEquals(expectedKotlinScript, actualKotlinScript) {
        "Kotlin scripts should be equal"
      }
    }
  }

  fun assertBuildSettings(
    groovyScript: String,
    kotlinScript: String,
    configure: GradleSettingScriptBuilder<*>.() -> Unit
  ) {
    assertBuildSettings(GradleVersion.current() to (groovyScript to kotlinScript), configure = configure)
  }
}