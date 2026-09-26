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

/** A root build with two subprojects and a buildSrc, as in KTIJ-38188 and KTIJ-38825. */
val GRADLE_BUILD_SRC_FIXTURE: GradleTestFixtureBuilder = GradleTestFixtureBuilder.create("GradleKotlinBuildSrcFixture") { gradleVersion ->
    withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
        setProjectName("GradleKotlinBuildSrcFixture")
        include(":app", ":lib")
    }
    withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
        withMavenCentral()
        withPostfix { code("myHelperMethod()") }
    }
    withBuildFile(gradleVersion, "buildSrc", gradleDsl = GradleDsl.KOTLIN) {
        withKotlinDsl()
        withMavenCentral()
    }
    withFile(
        "buildSrc/src/main/kotlin/Helper.kt", """
            fun myHelperMethod() {
                println("from buildSrc")
            }
        """.trimIndent()
    )
    withFile(
        "buildSrc/src/main/kotlin/Versions.kt", $$"""
            object Versions {
                const val KOTLIN = "2.3.20"
            }

            object Libs {
                object Kotlin {
                    const val STDLIB = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.KOTLIN}"
                }
            }

            object Plugins {
                object Id {
                    const val JAVA_LIBRARY = "java-library"
                }
            }
        """.trimIndent()
    )
    for (subproject in listOf("app", "lib")) {
        withBuildFile(gradleVersion, subproject, gradleDsl = GradleDsl.KOTLIN) {
            withPlugin { code("id(Plugins.Id.JAVA_LIBRARY)") }
            withPostfix {
                code("version = Versions.KOTLIN")
                code("description = Libs.Kotlin.STDLIB")
            }
        }
    }
}