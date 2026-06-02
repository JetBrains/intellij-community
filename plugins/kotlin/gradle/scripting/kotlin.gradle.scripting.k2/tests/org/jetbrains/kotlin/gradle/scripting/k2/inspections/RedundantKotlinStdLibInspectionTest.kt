// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.K2GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.codeInspection.GradleRedundantKotlinStdLibInspection
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.AllGradleVersionsSource
import org.jetbrains.plugins.gradle.testFramework.util.KOTLIN_DSL_DELEGATING_PROPERTY_SUPPORTED_VERSIONS
import org.jetbrains.plugins.gradle.testFramework.util.KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS
import org.jetbrains.plugins.gradle.testFramework.util.VERSION_CATALOGS_SUPPORTED_VERSIONS
import org.jetbrains.plugins.gradle.testFramework.util.assertThatKotlinDslScriptsModelImportIsSupported
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.withSettingsFile
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.jupiter.params.ParameterizedTest

class RedundantKotlinStdLibInspectionTest : K2GradleCodeInsightTestCase() {

    private fun runTest(
        gradleVersion: GradleVersion,
        projectFixture: GradleTestFixtureBuilder,
        test: () -> Unit
    ) {
        assertThatKotlinDslScriptsModelImportIsSupported(gradleVersion)
        test(gradleVersion, projectFixture) {
            codeInsightFixture.enableInspections(GradleRedundantKotlinStdLibInspection::class.java)
            test()
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDifferentVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testKotlinMethod(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testKotlinMethodNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testKotlinMethodDifferentVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDependencyNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDependencyNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDependencyNamedArgumentsNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDependencyPositionalArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDifferentConfiguration(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS, KOTLIN_DSL_DELEGATING_PROPERTY_SUPPORTED_VERSIONS)
    fun testCustomConfigurationString(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val customConf by configurations.creating {}
                dependencies { 
                    <warning>"customConf"("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS, KOTLIN_DSL_DELEGATING_PROPERTY_SUPPORTED_VERSIONS)
    fun testCustomConfiguration(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDependencyWithExtraArgumentsInClosure(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDependencyWithExtraArgumentsInMap(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSingeStringFalsePositive(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testKotlinMethodFalsePositive(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testNamedArgumentsFalsePositive(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testFalsePositives(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testCompileOnly(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS, KOTLIN_DSL_DELEGATING_PROPERTY_SUPPORTED_VERSIONS)
    fun testCompileOnlyCustomSourceSet(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val customSourceSet by sourceSets.creating {}
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
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testDependencyFromVersionCatalog(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_AND_VERSION_CATALOGS_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies {
                    <warning>api(libs.kotlin.std.lib.simple)</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testDependencyFromVersionCatalogNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_AND_VERSION_CATALOGS_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies {
                    api(libs.kotlin.std.lib.noVersion)
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testDependencyFromVersionCatalogModuleAndVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_AND_VERSION_CATALOGS_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies {
                    <warning>api(libs.kotlin.std.lib.moduleVersion)</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testDependencyFromVersionCatalogModuleAndVersionReference(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_AND_VERSION_CATALOGS_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies {
                    <warning>api(libs.kotlin.std.lib.moduleVersionRef)</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testDependencyFromVersionCatalogFull(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_AND_VERSION_CATALOGS_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies {
                    <warning>api(libs.kotlin.std.lib.groupNameVersion)</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testDependencyFromVersionCatalogFullVersionReference(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_AND_VERSION_CATALOGS_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies {
                    <warning>api(libs.kotlin.std.lib.groupNameVersionRef)</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testDependencyFromVersionCatalogMultilineString(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_AND_VERSION_CATALOGS_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                dependencies {
                    <warning>api(libs.kotlin.std.lib.multiline)</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS, KOTLIN_DSL_DELEGATING_PROPERTY_SUPPORTED_VERSIONS)
    fun testCustomConfigurationVersionCatalog(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_AND_VERSION_CATALOGS_FIXTURE) {
            testHighlighting(
                """
                plugins { id("org.jetbrains.kotlin.jvm").version("2.2.0") }
                val customConf by configurations.creating {}
                dependencies {
                    <warning>customConf(libs.kotlin.std.lib.simple)</warning>
                }
                """.trimIndent()
            )
        }
    }

    // PLUGIN DETECTION TESTS

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testNoPlugin(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDifferentPlugin(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testPluginIdNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testPluginFromKotlinMethod(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testPluginFromKotlinMethodBinary(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testPluginFromKotlinMethodNotApplied(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testPluginFromKotlinMethodNotAppliedBinary(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testPluginFromKotlinMethodNotAppliedDotBinary(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testPluginFromVersionCatalog(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_AND_VERSION_CATALOGS_FIXTURE) {
            testHighlighting(
                """
                plugins { alias(${libsInPlugins(gradleVersion)}.plugins.kotlinJvm) }
                dependencies {
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testPluginFromVersionCatalogFull(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_AND_VERSION_CATALOGS_FIXTURE) {
            testHighlighting(
                """
                plugins { alias(${libsInPlugins(gradleVersion)}.plugins.kotlinJvmFull) }
                dependencies {
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testPluginFromVersionCatalogFullWithVersionReference(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_AND_VERSION_CATALOGS_FIXTURE) {
            testHighlighting(
                """
                plugins { alias(${libsInPlugins(gradleVersion)}.plugins.kotlinJvmFullRef) }
                dependencies {
                    <warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testPluginIdNotApplied(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testPluginIdApplied(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testPluginIdMethodBinary(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testPluginIdMethodNotAppliedBinary(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testPluginIdMethodNotAppliedDotBinary(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testPluginFromVersionCatalogNotApplied(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_AND_VERSION_CATALOGS_FIXTURE) {
            testHighlighting(
                """
                plugins {
                    alias(${libsInPlugins(gradleVersion)}.plugins.kotlinJvm).apply(false)
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
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testPluginFromVersionCatalogNotAppliedBinary(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_AND_VERSION_CATALOGS_FIXTURE) {
            testHighlighting(
                """
                plugins {
                    alias(${libsInPlugins(gradleVersion)}.plugins.kotlinJvm) apply false
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDifferentVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameGroupVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDifferentGroupVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameNameVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDifferentNameVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameVersionVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDifferentVersionVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameGroupValNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            testHighlighting(
                """
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDifferentGroupValNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            testHighlighting(
                """
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameNameValNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            testHighlighting(
                """
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDifferentNameValNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            testHighlighting(
                """
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameVersionValNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            testHighlighting(
                """
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDifferentVersionValNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
            testHighlighting(
                """
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testKotlinMethodSameNameVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testKotlinMethodSameVersionVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testKotlinMethodDifferentVersionVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testQuickFixRemove(gradleVersion: GradleVersion) {
        runTest(gradleVersion, WITH_CUSTOM_CONFIGURATIONS_FIXTURE) {
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
        private val DISABLED_DEFAULT_STDLIB_FIXTURE =
            GradleTestFixtureBuilder.create("disabled-default-stdlib") { gradleVersion ->
                withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    setProjectName("disabled-default-stdlib")
                }
                withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    withKotlinJvmPlugin()
                }
                withFile("gradle.properties", "kotlin.stdlib.default.dependency=false")
            }

        /**
         * Cause: https://github.com/gradle/gradle/issues/22797
         */
        private fun libsInPlugins(gradleVersion: GradleVersion) =
            if (gradleVersion < GradleVersion.version("8.1")) "<error>libs</error>"
            else "libs"
    }
}