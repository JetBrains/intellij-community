// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isJunit5Supported
import org.jetbrains.plugins.gradle.testFramework.util.*

abstract class GradleExecutionTestCase : GradleExecutionBaseTestCase() {

  val jUnitTestAnnotationClass: String
    get() = when (isJunit5Supported(gradleVersion)) {
      true -> "org.junit.jupiter.api.Test"
      else -> "org.junit.Test"
    }

  val jUnitIgnoreAnnotationClass: String
    get() = when (isJunit5Supported(gradleVersion)) {
      true -> "org.junit.jupiter.api.Disabled"
      else -> "org.junit.Ignore"
    }

  fun isPerTaskOutputSupported(): Boolean = isGradleAtLeast("4.7")

  fun isBuildCompilationReportSupported(): Boolean = isGradleAtLeast("8.11")

  fun isBuiltInTestEventsUsed(): Boolean = isGradleAtLeast("7.6")

  fun isIntellijTestEventsUsed(): Boolean = !isBuiltInTestEventsUsed()

  fun isTestLauncherUsed(): Boolean = isGradleAtLeast("8.3")

  fun isOpentest4jSupportedByGradleJunit4Integration(): Boolean = isGradleAtLeast("8.4")

  fun testJunit5Project(gradleVersion: GradleVersion, action: () -> Unit) {
    assumeThatJunit5IsSupported(gradleVersion)
    testJavaProject(gradleVersion, action)
  }

  fun testJunit4Project(gradleVersion: GradleVersion, action: () -> Unit) {
    test(gradleVersion, JAVA_JUNIT4_FIXTURE, action)
  }

  fun testJunit4Opentest4jProject(gradleVersion: GradleVersion, action: () -> Unit) {
    test(gradleVersion, JAVA_JUNIT4_OPENTEST4J_FIXTURE, action)
  }

  fun testTestNGProject(gradleVersion: GradleVersion, action: () -> Unit) {
    test(gradleVersion, JAVA_TESTNG_FIXTURE, action)
  }

  fun testJunit5AssertJProject(gradleVersion: GradleVersion, action: () -> Unit) {
    assumeThatJunit5IsSupported(gradleVersion)
    test(gradleVersion, JAVA_JUNIT5_ASSERTJ_FIXTURE, action)
  }

  fun testSpockProject(gradleVersion: GradleVersion, action: () -> Unit) {
    assumeThatSpockIsSupported(gradleVersion)
    test(gradleVersion, GROOVY_SPOCK_FIXTURE, action)
  }

  fun testRobolectricProject(gradleVersion: GradleVersion, action: () -> Unit) {
    assumeThatRobolectricIsSupported(gradleVersion)
    test(gradleVersion, JAVA_ROBOLECTRIC_FIXTURE, action)
  }

  companion object {

    private val JAVA_JUNIT5_ASSERTJ_FIXTURE = GradleTestFixtureBuilder.create("java-plugin-junit5-assertj-project") { gradleVersion ->
      withSettingsFile {
        setProjectName("java-plugin-junit5-assertj-project")
      }
      withBuildFile(gradleVersion) {
        withJavaPlugin()
        withJUnit5()
        addTestImplementationDependency("org.assertj:assertj-core:3.24.2")
      }
      withDirectory("src/main/java")
      withDirectory("src/test/java")
    }

    private val JAVA_JUNIT4_FIXTURE = GradleTestFixtureBuilder.create("java-plugin-junit4-project") { gradleVersion ->
      withSettingsFile {
        setProjectName("java-plugin-junit4-project")
      }
      withBuildFile(gradleVersion) {
        withJavaPlugin()
        withJUnit4()
      }
      withDirectory("src/main/java")
      withDirectory("src/test/java")
    }

    private val JAVA_JUNIT4_OPENTEST4J_FIXTURE = GradleTestFixtureBuilder.create("java-plugin-junit4-opentest4j-project") { gradleVersion ->
      withSettingsFile {
        setProjectName("java-plugin-junit4-opentest4j-project")
      }
      withBuildFile(gradleVersion) {
        withJavaPlugin()
        withJUnit4()
        addTestImplementationDependency("org.opentest4j:opentest4j:1.3.0")
      }
      withDirectory("src/main/java")
      withDirectory("src/test/java")
    }

    private val JAVA_TESTNG_FIXTURE = GradleTestFixtureBuilder.create("java-plugin-testng-project") { gradleVersion ->
      withSettingsFile {
        setProjectName("java-plugin-testng-project")
      }
      withBuildFile(gradleVersion) {
        withJavaPlugin()
        withMavenCentral()
        addImplementationDependency("org.slf4j:slf4j-log4j12:2.0.5")
        addTestImplementationDependency("org.testng:testng:7.5")
        configureTestTask {
          call("useTestNG")
        }
      }
      withDirectory("src/main/java")
      withDirectory("src/test/java")
    }

    private val GROOVY_SPOCK_FIXTURE = GradleTestFixtureBuilder.create("groovy-plugin-spock-project") { gradleVersion ->
      withSettingsFile {
        setProjectName("groovy-plugin-spock-project")
      }
      withBuildFile(gradleVersion) {
        withGroovyPlugin("3.0.0")
        addTestImplementationDependency(call("platform", "org.spockframework:spock-bom:2.1-groovy-3.0"))
        addTestImplementationDependency("org.spockframework:spock-core:2.1-groovy-3.0")
        withJUnit()
      }
      withDirectory("src/main/groovy")
      withDirectory("src/test/groovy")
    }

    private val JAVA_ROBOLECTRIC_FIXTURE = GradleTestFixtureBuilder.create("java-plugin-robolectric-project") { gradleVersion ->
      withSettingsFile {
        setProjectName("java-plugin-robolectric-project")
      }
      withBuildFile(gradleVersion) {
        withJavaPlugin()
        withJUnit4()
        addTestImplementationDependency("org.robolectric:robolectric:4.11.1")
      }
      withDirectory("src/main/java")
      withDirectory("src/test/java")
    }
  }
}