// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle

import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile

val GRADLE_KMP_KOTLIN_FIXTURE: GradleTestFixtureBuilder = GradleTestFixtureBuilder.create("GradleKotlinFixture") { gradleVersion ->
    withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
        setProjectName("GradleKotlinFixture")
        include("module1", ":module1:a-module11", ":module1:a-module11:module111")
        enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
    }
    withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
        withKotlinMultiplatformPlugin()
        withMavenCentral()
    }
    withBuildFile(gradleVersion, "buildSrc", gradleDsl = GradleDsl.KOTLIN) {
        withKotlinDsl()
    }
    withBuildFile(gradleVersion, "module1", gradleDsl = GradleDsl.KOTLIN) {
        withKotlinMultiplatformPlugin()
        withMavenCentral()
    }
    withBuildFile(gradleVersion, "module1/a-module11", gradleDsl = GradleDsl.KOTLIN) {
        withKotlinMultiplatformPlugin()
        withMavenCentral()
    }
    withBuildFile(gradleVersion, "module1/a-module11/module111", gradleDsl = GradleDsl.KOTLIN) {
        withKotlinMultiplatformPlugin()
        withMavenCentral()
    }
    withFile(
        "gradle/libs.versions.toml",/* language=TOML */
        """
                [libraries]
                some_test-library = { module = "org.junit.jupiter:junit-jupiter" }
                [plugins]
                kotlin = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin"}
                [versions]
                test_library-version = "1.0"
                kotlin = "1.9.24"
                """.trimIndent()
    )
    withFile(
        "gradle.properties", """
                kotlin.code.style=official
                """.trimIndent()
    )
    withFile(
        "buildSrc/src/main/kotlin/MyTask.kt", """
                    import org.gradle.api.DefaultTask

                    abstract class MyTask : DefaultTask() {
                      init {
                        val runtimeClassPath = project.configurations.named("runtimeClasspath")
                      }
                    }
                """.trimIndent()
    )

    withFile("buildSrc/src/main/kotlin/conventions.gradle.kts","""
                plugins {
                    application
                }
            """.trimIndent())

    withDirectory("src/main/kotlin")
}

val GRADLE_KOTLIN_FIXTURE: GradleTestFixtureBuilder = GradleTestFixtureBuilder.create("GradleKotlinFixture") { gradleVersion ->
    withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
        setProjectName("GradleKotlinFixture")
        include(":module1")
        enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
    }
    withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
        withKotlinDsl()
        withMavenCentral()
    }
    withBuildFile(gradleVersion, "module1", gradleDsl = GradleDsl.KOTLIN) {
        withKotlinDsl()
        withMavenCentral()
    }
    withFile(
        "gradle.properties", """
                kotlin.code.style=official
                """.trimIndent()
    )
}