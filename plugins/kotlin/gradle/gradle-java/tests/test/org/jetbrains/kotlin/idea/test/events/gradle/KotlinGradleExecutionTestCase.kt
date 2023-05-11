// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.events.gradle

import com.intellij.testFramework.RunAll.Companion.runAll
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.plugins.gradle.execution.test.events.GradleExecutionTestCase
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile

abstract class KotlinGradleExecutionTestCase : GradleExecutionTestCase() {

    override fun tearDown() {
        runAll(
          { super.tearDown() },
          { KotlinSdkType.removeKotlinSdkInTests() }
        )
    }

    fun testKotlinProject(gradleVersion: GradleVersion, test: () -> Unit) =
      test(gradleVersion, KOTLIN_PROJECT, test)

    fun testKotlinJunit5Project(gradleVersion: GradleVersion, action: () -> Unit) {
        assertJunit5IsSupported(gradleVersion)
        testKotlinProject(gradleVersion, action)
    }

    fun testKotlinJunit4Project(gradleVersion: GradleVersion, action: () -> Unit) {
        test(gradleVersion, KOTLIN_JUNIT4_FIXTURE, action)
    }

    fun testKotlinTestNGProject(gradleVersion: GradleVersion, action: () -> Unit) {
        test(gradleVersion, KOTLIN_TESTNG_FIXTURE, action)
    }

    companion object {

        private val KOTLIN_PROJECT = GradleTestFixtureBuilder.create("kotlin-plugin-project") { gradleVersion ->
            withSettingsFile {
                setProjectName("kotlin-plugin-project")
            }
            withBuildFile(gradleVersion) {
                withKotlinJvmPlugin()
                withJUnit()
            }
            withDirectory("src/main/kotlin")
            withDirectory("src/test/kotlin")
        }

        private val KOTLIN_JUNIT4_FIXTURE =
          GradleTestFixtureBuilder.create("kotlin-plugin-junit4-project") { gradleVersion ->
              withSettingsFile {
                  setProjectName("kotlin-plugin-junit4-project")
              }
              withBuildFile(gradleVersion) {
                  withKotlinJvmPlugin()
                  withJUnit4()
              }
              withDirectory("src/main/kotlin")
              withDirectory("src/test/kotlin")
          }

        private val KOTLIN_TESTNG_FIXTURE = GradleTestFixtureBuilder.create("kotlin-plugin-testng-project") { gradleVersion ->
            withSettingsFile {
                setProjectName("kotlin-plugin-testng-project")
            }
            withBuildFile(gradleVersion) {
                withKotlinJvmPlugin()
                withMavenCentral()
                addImplementationDependency("org.slf4j:slf4j-log4j12:2.0.5")
                addTestImplementationDependency("org.testng:testng:7.5")
                configureTestTask {
                    call("useTestNG")
                }
            }
            withDirectory("src/main/kotlin")
            withDirectory("src/test/kotlin")
        }
    }
}