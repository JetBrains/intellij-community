// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.importing.GradleBuildScriptBuilder.Companion.buildscript
import org.junit.Test

class GradleBuildScriptBuilderTest {
  @Test
  fun `test empty build script`() {
    assertThat(buildscript {})
      .isEqualTo("")
  }

  @Test
  fun `test build script with plugins block`() {
    assertThat(
      GradleBuildScriptBuilder()
        .addImport("org.example.Class1")
        .addImport("org.example.Class2")
        .addPlugin("id 'plugin-id'")
        .addRepository("repositoryCentral()")
        .addDependency("dependency 'my-dependency-id'")
        .addPrefix("")
        .withPrefix { call("println", "'Hello, Prefix!'") }
        .withPostfix { call("println", "'Hello, Postfix!'") }
        .generate()
    ).isEqualTo("""
      import org.example.Class1
      import org.example.Class2
      plugins {
          id 'plugin-id'
      }
      
      println 'Hello, Prefix!'
      repositories {
          repositoryCentral()
      }
      dependencies {
          dependency 'my-dependency-id'
      }
      println 'Hello, Postfix!'
    """.trimIndent())
  }

  @Test
  fun `test build script with buildscript block`() {
    assertThat(
      GradleBuildScriptBuilder()
        .addBuildScriptPrefix("println 'Hello, Prefix!'")
        .withBuildScriptRepository { call("repo", "file('build/repo')") }
        .withBuildScriptDependency { call("classpath", "file('build/targets/org/classpath/archive.jar')") }
        .addBuildScriptPostfix("println 'Hello, Postfix!'")
        .applyPlugin("'gradle-build'")
        .addImport("org.classpath.Build")
        .withPrefix {
          block("Build.configureSuperGradleBuild") {
            call("makeBeautiful")
          }
        }
        .generate()
    ).isEqualTo("""
      import org.classpath.Build
      buildscript {
          println 'Hello, Prefix!'
          repositories {
              repo file('build/repo')
          }
          dependencies {
              classpath file('build/targets/org/classpath/archive.jar')
          }
          println 'Hello, Postfix!'
      }
      apply plugin: 'gradle-build'
      Build.configureSuperGradleBuild {
          makeBeautiful()
      }
    """.trimIndent())
  }
}