// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule
import org.junit.jupiter.api.Assertions

abstract class GradleBuildScriptBuilderTestCase {

  fun assertBuildScript(
    vararg cases: Pair<GradleVersion, Pair<String, String>>,
    configure: GradleBuildScriptBuilder<*>.() -> Unit
  ) {
    val casesForSupportedGradleVersions = VersionMatcherRule.SUPPORTED_GRADLE_VERSIONS
      .mapNotNull {
        val gradleVersion = GradleVersion.version(it)
        cases.asSequence()
          .filter { (version, _) -> version <= gradleVersion }
          .maxByOrNull { (version, _) -> version }
          ?.let { (_, case) -> gradleVersion to case }
      }
    for ((gradleVersion, expectedScripts) in casesForSupportedGradleVersions + cases) {
      val (expectedGroovyScript, expectedKotlinScript) = expectedScripts
      val actualGroovyScript = GradleBuildScriptBuilder.create(gradleVersion, useKotlinDsl = false).apply(configure).generate()
      val actualKotlinScript = GradleBuildScriptBuilder.create(gradleVersion, useKotlinDsl = true).apply(configure).generate()
      Assertions.assertEquals(expectedGroovyScript, actualGroovyScript) {
        "$gradleVersion: Groovy scripts should be equal"
      }
      Assertions.assertEquals(expectedKotlinScript, actualKotlinScript) {
        "$gradleVersion: Kotlin scripts should be equal"
      }
    }
  }

  fun assertBuildScript(
    groovyScript: String,
    kotlinScript: String,
    configure: GradleBuildScriptBuilder<*>.() -> Unit
  ) {
    val gradleVersion = GradleVersion.version(VersionMatcherRule.SUPPORTED_GRADLE_VERSIONS.first())
    assertBuildScript(gradleVersion to (groovyScript to kotlinScript), configure = configure)
  }
}