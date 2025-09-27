// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.gradleCodeInsightCommon.inspections.RedundantKotlinStdLibInspection
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.junit.jupiter.params.ParameterizedTest

class RedundantKotlinStdLibInspectionTest : K2GradleCodeInsightTestCase() {

    private fun runTest(
        gradleVersion: GradleVersion,
        projectFixture: GradleTestFixtureBuilder,
        test: () -> Unit
    ) {
        test(gradleVersion, projectFixture) {
            codeInsightFixture.enableInspections(RedundantKotlinStdLibInspection::class.java)
            test()
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testSameVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDifferentVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DIFFERENT_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testKotlinMethod(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>api(kotlin("stdlib", "2.2.0"))</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testKotlinMethodNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>api(kotlin("stdlib"))</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testKotlinMethodDifferentVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    api(kotlin("stdlib", "2.1.0"))
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testSingeStringFalsePositive(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.0"")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testKotlinMethodFalsePositive(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    api(kotlin("stdlib-jdk8"))
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testPluginIdNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm") }
                dependencies { 
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDependencyNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDependencyNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDependencyNamedArgumentsNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDifferentConfiguration(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testCustomConfiguration(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>"customConf"("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNoKotlinPlugin(gradleVersion: GradleVersion) {
        runTest(gradleVersion, JAVA_PLUGIN_WITH_KOTLIN_STDLIB_FIXTURE) {
            testHighlighting(
                """
                plugins { id("java") }
                dependencies { 
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testPluginFromKotlinMethod(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { kotlin("jvm").version("2.2.0") }
                dependencies { 
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testPluginFromVersionCatalog(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { alias(libs.plugins.kotlinJvm) }
                dependencies { 
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testPluginFromVersionCatalogFull(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { alias(libs.plugins.kotlinJvmFull) }
                dependencies { 
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testPluginFromVersionCatalogFullWithVersionReference(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { alias(libs.plugins.kotlinJvmFullRef) }
                dependencies { 
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testPluginIdNotApplied(gradleVersion: GradleVersion) {
        runTest(gradleVersion, NOT_APPLIED_FIXTURE) {
            testHighlighting(
                """
                plugins { 
                    id("org.jetbrains.kotlin.jvm").version("2.2.0").apply(false)
                    id("java")
                }
                dependencies { 
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testPluginFromVersionCatalogNotApplied(gradleVersion: GradleVersion) {
        runTest(gradleVersion, NOT_APPLIED_FIXTURE) {
            testHighlighting(
                """
                plugins { 
                    alias(libs.plugins.kotlinJvm).apply(false)
                    id("java")
                }
                dependencies { 
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDependencyFromVersionCatalog(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>api(libs.kotlin.std.lib1)</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDependencyFromVersionCatalogNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>api(libs.kotlin.std.lib2)</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDependencyFromVersionCatalogModuleAndVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>api(libs.kotlin.std.lib3)</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDependencyFromVersionCatalogFull(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>api(libs.kotlin.std.lib4)</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDependencyFromVersionCatalogFullVersionReference(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>api(libs.kotlin.std.lib5)</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDependencyFromVersionCatalogMultilineString(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>api(libs.kotlin.std.multiline)</warning>
                }
                """.trimIndent()
            )
        }
    }

    // should not warn as the overriding kotlin-stdlib dependency is a bit different
    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDependencyWithExtraArgumentsInClosure(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0") {
                        exclude(group = "org.jetbrains", module = "annotations")
                    }
                }
                """.trimIndent()
            )
        }
    }

    // should not warn as the overriding kotlin-stdlib dependency is a bit different
    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDependencyWithExtraArgumentsInMap(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0", because = "why not")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testDisabledDefaultStdLib(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DISABLED_DEFAULT_STDLIB_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testFalsePositives(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
                    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.0")
                    api("org.example:some:1.0.0")")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testQuickFixRemove(gradleVersion: GradleVersion) {
        runTest(gradleVersion, SAME_VERSION_FIXTURE) {
            testIntention(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies {
                    <caret>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent(),
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies {
                }
                """.trimIndent(),
                "Remove dependency"
            )
        }
    }

    companion object {
        private val SAME_VERSION_FIXTURE = GradleTestFixtureBuilder.create("redundant_kotlin_stdlib") { gradleVersion ->
            withFile(
                "gradle/libs.versions.toml", /* language=TOML */ """
                [versions]
                kotlin = "2.2.0"
                [plugins]
                kotlinJvm = "org.jetbrains.kotlin.jvm:2.2.0"
                kotlinJvmFull = { id = "org.jetbrains.kotlin.jvm", version = "2.2.0" }
                kotlinJvmFullRef = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
                [libraries]
                kotlin-std-lib1 = "org.jetbrains.kotlin:kotlin-stdlib:2.2.0"
                kotlin-std-lib2.module = "org.jetbrains.kotlin:kotlin-stdlib"
                kotlin-std-lib3 = { module = "org.jetbrains.kotlin:kotlin-stdlib", version = "2.2.0" }
                kotlin-std-lib4 = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0" }
                kotlin-std-lib5 = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version.ref = "kotlin" }
                kotlin-std-multiline = ""${'"'}org.jetbrains.kotlin
                :kotlin-stdlib
                :2.2.0
                ""${'"'}
                """.trimIndent()
            )
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withKotlinJvmPlugin("2.2.0")
                withPrefix { code("val customConf by configurations.creating {}") }
            }
        }
        private val DIFFERENT_VERSION_FIXTURE = GradleTestFixtureBuilder.create("different_kotlin_stdlib") { gradleVersion ->
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withKotlinJvmPlugin("2.2.0")
                addApiDependency("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
            }
        }
        private val JAVA_PLUGIN_WITH_KOTLIN_STDLIB_FIXTURE = GradleTestFixtureBuilder.create("just_kotlin_stdlib") { gradleVersion ->
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withJavaPlugin()
                addApiDependency("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                withPrefix { code("val api by configurations.creating {}") }
            }
        }
        private val DISABLED_DEFAULT_STDLIB_FIXTURE = GradleTestFixtureBuilder.create("disabled_default_stdlib") { gradleVersion ->
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withKotlinJvmPlugin("2.2.0")
                addApiDependency("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            }
            withFile("gradle.properties", "kotlin.stdlib.default.dependency=false")
        }
        private val NOT_APPLIED_FIXTURE = GradleTestFixtureBuilder.create("not_applied_kotlin_jvm") { gradleVersion ->
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withPlugin {
                    infixCall(
                        infixCall(
                            call("kotlin", "jvm"),
                            "version",
                            string("2.2.0")
                        ),
                        "apply",
                        boolean(false)
                    )
                }
                withJavaPlugin()
                withPrefix { code("val api by configurations.creating {}") }
                addApiDependency("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
            }
        }
    }
}