// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleRedundantKotlinStdLibInspection
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.assumeThatGradleIsAtLeast
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.junit.jupiter.params.ParameterizedTest

class RedundantKotlinStdLibInspectionTest : K2GradleCodeInsightTestCase() {

    private fun runTest(
        gradleVersion: GradleVersion,
        projectFixture: GradleTestFixtureBuilder,
        test: () -> Unit
    ) {
        assumeThatGradleIsAtLeast(gradleVersion, "9.0.0") { "Best practice added in Gradle 9.0.0" }
        test(gradleVersion, projectFixture) {
            codeInsightFixture.enableInspections(GradleRedundantKotlinStdLibInspection::class.java)
            (codeInsightFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
            test()
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testSameVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testDifferentVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testKotlinMethod(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testKotlinMethodNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    api(kotlin("stdlib"))
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testKotlinMethodDifferentVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testDependencyNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    api("org.jetbrains.kotlin:kotlin-stdlib")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDependencyNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testDependencyNamedArgumentsNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDependencyPositionalArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>api("org.jetbrains.kotlin", "kotlin-stdlib", "2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDifferentConfiguration(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testCustomConfigurationString(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testCustomConfiguration(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val customConf by configurations.creating {}
                dependencies { 
                    <warning>customConf("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    // should not warn as the overriding kotlin-stdlib dependency is a bit different
    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDependencyWithExtraArgumentsInClosure(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testDependencyWithExtraArgumentsInMap(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0", ext = "why not")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
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
    @AllGradleVersionsSource
    fun testSingeStringFalsePositive(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testKotlinMethodFalsePositive(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    api(kotlin("stdlib-jdk8", "2.2.0"))
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNamedArgumentsFalsePositive(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8", version = "2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testFalsePositives(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.0")
                    api("org.example:some:1.0.0")
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testCompileOnly(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testCompileOnlyCustomSourceSet(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies { 
                    "customSourceSetCompileOnly"("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    // VERSION CATALOG RESOLVING TESTS

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDependencyFromVersionCatalog(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testDependencyFromVersionCatalogNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies {
                    api(libs.kotlin.std.lib2)
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDependencyFromVersionCatalogModuleAndVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testDependencyFromVersionCatalogFull(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testDependencyFromVersionCatalogFullVersionReference(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testDependencyFromVersionCatalogMultilineString(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testCustomConfigurationVersionCatalog(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val customConf by configurations.creating {}
                dependencies {
                    <warning>customConf(libs.kotlin.std.lib1)</warning>
                }
                """.trimIndent()
            )
        }
    }

    // PLUGIN DETECTION TESTS

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testNoPlugin(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDifferentPlugin(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testPluginIdNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm") }
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testPluginFromKotlinMethod(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testPluginFromKotlinMethodBinary(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { kotlin("jvm") version "2.2.0" }
                dependencies {
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testPluginFromKotlinMethodNotApplied(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { kotlin("jvm").version("2.2.0").apply(false) }
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testPluginFromKotlinMethodNotAppliedBinary(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { kotlin("jvm") version "2.2.0" apply false }
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testPluginFromKotlinMethodNotAppliedDotBinary(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { kotlin("jvm").version("2.2.0") apply false }
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testPluginFromVersionCatalog(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testPluginFromVersionCatalogFull(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testPluginFromVersionCatalogFullWithVersionReference(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
    @AllGradleVersionsSource
    fun testPluginIdNotApplied(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins {
                    id("org.jetbrains.kotlin.jvm").version("2.2.0").apply(false)
                }
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testPluginIdApplied(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins {
                    id("org.jetbrains.kotlin.jvm").version("2.2.0").apply(true)
                }
                dependencies {
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testPluginIdMethodBinary(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm") version "2.2.0" }
                dependencies {
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testPluginIdMethodNotAppliedBinary(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm") version "2.2.0" apply false }
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testPluginIdMethodNotAppliedDotBinary(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") apply false }
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testPluginFromVersionCatalogNotApplied(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins {
                    alias(libs.plugins.kotlinJvm).apply(false)
                }
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testPluginFromVersionCatalogNotAppliedBinary(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins {
                    alias(libs.plugins.kotlinJvm) apply false
                }
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    // STRING RESOLVING

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testSameVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val coordinates = "org.jetbrains.kotlin:kotlin-stdlib:2.2.0"
                dependencies {
                    <warning>api(coordinates)</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDifferentVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val coordinates = "org.jetbrains.kotlin:kotlin-stdlib:2.1.0"
                dependencies {
                    api(coordinates)
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testSameGroupVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                $$"""
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val group = "org.jetbrains.kotlin"
                dependencies {
                    <warning>api("$group:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDifferentGroupVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                $$"""
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val group = "org.other.kotlin"
                dependencies {
                    api("$group:kotlin-stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testSameNameVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                $$"""
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val name = "kotlin-stdlib"
                dependencies {
                    <warning>api("org.jetbrains.kotlin:$name:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDifferentNameVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                $$"""
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val name = "kotlin-stdlib-jdk8"
                dependencies {
                    api("org.jetbrains.kotlin:$name:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testSameVersionVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                $$"""
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val version = "2.2.0"
                dependencies {
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib:$version")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDifferentVersionVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                $$"""
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val version = "2.1.0"
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:$version")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testSameGroupValNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                $$"""
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val group = "org.jetbrains.kotlin"
                dependencies {
                    <warning>api(group = group, name = "kotlin-stdlib", version = "2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDifferentGroupValNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                $$"""
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val group = "org.other.kotlin"
                dependencies {
                    api(group = group, name = "kotlin-stdlib", version = "2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testSameNameValNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                $$"""
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val name = "kotlin-stdlib"
                dependencies {
                    <warning>api(group = "org.jetbrains.kotlin", name = name, version = "2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDifferentNameValNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                $$"""
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val name = "kotlin-stdlib-jdk8"
                dependencies {
                    api(group = "org.jetbrains.kotlin", name = name, version = "2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testSameVersionValNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                $$"""
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val version = "2.2.0"
                dependencies {
                    <warning>api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = version)</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testDifferentVersionValNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                $$"""
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val version = "2.1.0"
                dependencies {
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = version)
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testKotlinMethodSameNameVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val kotlinStdlib = "stdlib"
                dependencies {
                    <warning>api(kotlin(kotlinStdlib, "2.2.0"))</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testKotlinMethodSameVersionVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val version = "2.2.0"
                dependencies {
                    <warning>api(kotlin("stdlib", version))</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testKotlinMethodDifferentVersionVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val version = "2.1.0"
                dependencies {
                    api(kotlin("stdlib", version))
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    fun testQuickFixRemove(gradleVersion: GradleVersion) {
        runTest(gradleVersion, DEFAULT_FIXTURE) {
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
        private val DEFAULT_FIXTURE = GradleTestFixtureBuilder.create("redundant_kotlin_stdlib") { gradleVersion ->
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
                withPrefix { code("val customSourceSet by sourceSets.creating {}") }
            }
        }
        private val DISABLED_DEFAULT_STDLIB_FIXTURE = GradleTestFixtureBuilder.create("disabled_default_stdlib") { gradleVersion ->
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withKotlinJvmPlugin("2.2.0")
            }
            withFile("gradle.properties", "kotlin.stdlib.default.dependency=false")
        }
    }
}