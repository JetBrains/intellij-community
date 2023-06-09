// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile

internal object GradleTestFixtureBuilderProvider {
    val KOTLIN_PROJECT = GradleTestFixtureBuilder.create("kotlin-plugin-project") { gradleVersion ->
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

    val KOTLIN_JUNIT4_FIXTURE =
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

    val KOTLIN_TESTNG_FIXTURE = GradleTestFixtureBuilder.create("kotlin-plugin-testng-project") { gradleVersion ->
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