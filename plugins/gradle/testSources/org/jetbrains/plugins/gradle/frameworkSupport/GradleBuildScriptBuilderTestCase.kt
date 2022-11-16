// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.*
import org.junit.jupiter.api.Assertions.assertEquals

abstract class GradleBuildScriptBuilderTestCase {

  fun assertBuildScript(
    vararg cases: Pair<GradleVersion, Pair<String, String>>,
    configure: GradleBuildScriptBuilder<*>.() -> Unit
  ) {
    for ((gradleVersion, expectedScripts) in cases) {
      val (expectedGroovyScript, expectedKotlinScript) = expectedScripts
      val actualGroovyScript = GroovyDslGradleBuildScriptBuilder.create(gradleVersion).apply(configure).generate()
      val actualKotlinScript = KotlinDslGradleBuildScriptBuilder.create(gradleVersion).apply(configure).generate()
      assertEquals(expectedGroovyScript, actualGroovyScript) { "Incorrect Groovy Gradle script for $gradleVersion" }
      assertEquals(expectedKotlinScript, actualKotlinScript) { "Incorrect Kotlin Gradle script for $gradleVersion" }
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