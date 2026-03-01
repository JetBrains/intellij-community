// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class GradleDCLImportingTest: GradleImportingTestCase() {

  @Test
  @TargetVersions("8.9+")
  // TODO: This test uses Internet repositories and is slow in CI environment.
  // references should be updated as soon as syntax is available in DCL
  fun `import basic java app project`() {
    val jvmEcosystemPluginVersion = when {
      GradleVersionUtil.isGradleAtLeast(currentGradleVersion, "9.5.0") -> "0.1.54"
      GradleVersionUtil.isGradleAtLeast(currentGradleVersion, "9.4.0") -> "0.1.50"
      else -> "0.1.21"
    }
    createProjectSubFile("gradle.properties", "org.gradle.kotlin.dsl.dcl=true")
    createProjectSubFile("settings.gradle.dcl", """
      pluginManagement {
          repositories {
              google()
              gradlePluginPortal()
          }
      }
      
      plugins {
          id("org.gradle.experimental.jvm-ecosystem").version("$jvmEcosystemPluginVersion")
      }
      
      rootProject.name = "test-dcl"
      """)
    createProjectSubFile("build.gradle.dcl", """
      javaApplication {
          javaVersion = 17
          mainClass = "com.example.App"

          dependencies {
              implementation("com.google.guava:guava:32.1.3-jre")
          }

          testing {
              // test on 21
              testJavaVersion = 21

              dependencies {
                  implementation("org.junit.jupiter:junit-jupiter:5.12.2")
              }
          }
      }
    """.trimIndent())
    createProjectSubFile("src/main/java/org/example/MyClass.java",
    """
      package com.example;
      class MyClass {}
    """.trimIndent())

    createProjectSubFile("src/test/java/org/example/Test.java",
                         """
                           package com.example;
                           import org.junit.Test;
                           public class Test {}
                         """.trimIndent())
    importProject()

    assertModules("test-dcl", "test-dcl.main", "test-dcl.test")
    assertModuleModuleDeps("test-dcl.test", "test-dcl.main")
    assertModuleLibDep("test-dcl.main", "Gradle: com.google.guava:guava:32.1.3-jre")
    assertModuleLibDep("test-dcl.test", "Gradle: org.junit.jupiter:junit-jupiter:5.12.2")
  }
}