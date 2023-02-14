// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.testFramework.util.buildScript
import org.junit.jupiter.api.Assertions.assertEquals

abstract class GradleBuildScriptBuilderTestCase {

  fun assertBuildScript(
    vararg cases: Pair<GradleVersion, Pair<String, String>>,
    configure: GradleBuildScriptBuilder<*>.() -> Unit
  ) {
    for ((gradleVersion, expectedScripts) in cases) {
      val (expectedGroovyScript, expectedKotlinScript) = expectedScripts
      assertEquals(expectedGroovyScript, buildScript(gradleVersion, useKotlinDsl = false, configure))
      assertEquals(expectedKotlinScript, buildScript(gradleVersion, useKotlinDsl = true, configure))
    }
  }

  fun assertBuildScript(
    groovyScript: String,
    kotlinScript: String,
    configure: GradleBuildScriptBuilder<*>.() -> Unit
  ) {
    assertBuildScript(GradleVersion.current() to (groovyScript to kotlinScript), configure = configure)
  }
}