// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.K2GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.codeInspection.GradleAvoidDuplicateDependenciesInspection
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

class KotlinAvoidDuplicateDependenciesInspectionTest : K2GradleCodeInsightTestCase() {

    private fun runTest(
        gradleVersion: GradleVersion,
        withVersionCatalogs: Boolean,
        test: () -> Unit
    ) {
        assertThatKotlinDslScriptsModelImportIsSupported(gradleVersion)
        val fixture = if (withVersionCatalogs) {
            WITH_CUSTOM_CONFIGURATIONS_AND_VERSION_CATALOGS_FIXTURE
        } else {
            WITH_CUSTOM_CONFIGURATIONS_FIXTURE
        }
        test(gradleVersion, fixture) {
            codeInsightFixture.enableInspections(GradleAvoidDuplicateDependenciesInspection::class.java)
            test()
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSingleDependency(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
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
    fun testDifferentDependencies(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
                    api("org.jetbrains.kotlin:kotlin-something:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSimpleSameDependency(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameDependencyDifferentConfigurations(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameDependencyInDifferentBlocks(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                }
                
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameDependencyConfigurationBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0") { exclude("something") }</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameDependencyNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameDependencyNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib")</weak_warning>
                    <weak_warning>api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib")</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDifferentVersions(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib")
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testSimpleVersionCatalogsResolve(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = true) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>api(libs.kotlin.std.lib.simple)</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testSimpleVersionCatalogsResolveNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = true) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib")</weak_warning>
                    <weak_warning>api(libs.kotlin.std.lib.noVersion)</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testVersionCatalogsResolve(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = true) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>api(libs.kotlin.std.lib.moduleVersion)</weak_warning>
                }
                """.trimIndent()
            )
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>api(libs.kotlin.std.lib.groupNameVersion)</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testVersionCatalogsResolveVersionRef(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = true) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>api(libs.kotlin.std.lib.moduleVersionRef)</weak_warning>
                }
                """.trimIndent()
            )
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>api(libs.kotlin.std.lib.groupNameVersionRef)</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS)
    fun testVersionCatalogsResolveMultiline(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = true) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>api(libs.kotlin.std.lib.multiline)</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameVersionVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    val versionRef = "2.2.0"
                    <weak_warning>api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = versionRef)</weak_warning>
                    <weak_warning>api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = versionRef)</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testStringInterpolation(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                $$"""
                dependencies {
                    val groupRef = "org.jetbrains.kotlin"
                    val nameRef = "kotlin-stdlib"
                    val versionRef = "2.2.0"
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>api("$groupRef:$nameRef:$versionRef")</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testNamedArgumentsVals(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    val groupRef = "org.jetbrains.kotlin"
                    val nameRef = "kotlin-stdlib"
                    val versionRef = "2.2.0"
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>api(group = groupRef, name = nameRef, version = versionRef)</weak_warning>
                }
                """.trimIndent()
            )
        }
    }


    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testCoordinateVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    val coordRef = "org.jetbrains.kotlin:kotlin-stdlib:2.2.0"
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>api(coordRef)</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDifferentVersionSameNameVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                $$"""
                dependencies {
                    val versionRef = "2.1.0"
                    api("org.jetbrains.kotlin:kotlin-stdlib:$versionRef")
                }
                dependencies {
                    val versionRef = "2.2.0"
                    api("org.jetbrains.kotlin:kotlin-stdlib:$versionRef")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDifferentVersionSameNameValNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    val versionRef = "2.1.0"
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = versionRef)
                }
                dependencies {
                    val versionRef = "2.2.0"
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = versionRef)
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS, KOTLIN_DSL_DELEGATING_PROPERTY_SUPPORTED_VERSIONS)
    fun testCustomConfigurationSimple(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                val customConf by configurations.creating {}
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>customConf("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(VERSION_CATALOGS_SUPPORTED_VERSIONS, KOTLIN_DSL_DELEGATING_PROPERTY_SUPPORTED_VERSIONS)
    fun testCustomConfigurationVersionCatalog(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = true) {
            testHighlighting(
                """
                val customConf by configurations.creating {}
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>customConf(libs.kotlin.std.lib.simple)</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameKotlinArg(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api(kotlin("stdlib"))</weak_warning>
                    <weak_warning>api(kotlin("stdlib"))</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testDifferentKotlinArg(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    api(kotlin("stdlib-jdk8"))
                    api(kotlin("stdlib"))
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testKotlinArgVar(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    var kotlinArg = "stdlib"
                    api(kotlin(kotlinArg))
                    api(kotlin(kotlinArg))
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameKotlinArgDifferentVals(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    val kotlinRef = "stdlib"
                    val kotlinRef2 = "stdlib"
                    <weak_warning>api(kotlin(kotlinRef))</weak_warning>
                    <weak_warning>api(kotlin(kotlinRef))</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameKotlinArgWithSameVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api(kotlin("stdlib", "2.2.0"))</weak_warning>
                    <weak_warning>api(kotlin("stdlib", "2.2.0"))</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameKotlinArgWithDifferentVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    api(kotlin("stdlib", "2.1.0"))
                    api(kotlin("stdlib", "2.2.0"))
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameKotlinArgWithSameVersionVals(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    val versionRef1 = "2.2.0"
                    val versionRef2 = "2.2.0"
                    <weak_warning>api(kotlin("stdlib", versionRef1))</weak_warning>
                    <weak_warning>api(kotlin("stdlib", versionRef2))</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testSameKotlinArgWithDifferentVersionVals(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    val versionRef1 = "2.1.0"
                    val versionRef2 = "2.2.0"
                    api(kotlin("stdlib", versionRef1))
                    api(kotlin("stdlib", versionRef2))
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testEquivalentKotlinArgWithSingleString(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api(kotlin("stdlib"))</weak_warning>
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib")</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testEquivalentKotlinArgWithNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api(kotlin("stdlib"))</weak_warning>
                    <weak_warning>api("org.jetbrains.kotlin", "kotlin-stdlib")</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testEquivalentKotlinArgWithSingleStringAndVersions(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api(kotlin("stdlib", "2.2.0"))</weak_warning>
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testEquivalentKotlinArgWithNamedArgumentsAndVersions(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    <weak_warning>api(kotlin("stdlib", "2.2.0"))</weak_warning>
                    <weak_warning>api("org.jetbrains.kotlin", "kotlin-stdlib", "2.2.0")</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testNotEquivalentKotlinArg(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    api(kotlin("stdlib", "2.2.0"))
                    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.0")
                    api("org.jetbrains.kotlin:kotlin-stdlib")
                    api("org.jetbrains:kotlin-stdlib:2.2.0")
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
                    api("org.jetbrains.kotlin:stdlib:2.2.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testIgnoreOtherDependencyBlocks(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                buildscript {
                    dependencies {
                        classpath("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                    }
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
    fun testRemoveSimple(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testIntention(
                """
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret>
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent(),
                """
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent(),
                "Remove exact duplicates"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testRemoveSimpleMultiple(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testIntention(
                """
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret>
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent(),
                """
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent(),
                "Remove exact duplicates"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testRemoveSimpleUnselected(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testIntention(
                """
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                    implementation("org:another:1.0.0")
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret>
                    implementation("org:yet-another:1.0.0")
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent(),
                """
                dependencies {
                    implementation("org:another:1.0.0")
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                    implementation("org:yet-another:1.0.0")
                }
                """.trimIndent(),
                "Remove exact duplicates"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testRemoveNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testIntention(
                """
                dependencies {
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")<caret>
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")
                }
                """.trimIndent(),
                """
                dependencies {
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")
                }
                """.trimIndent(),
                "Remove exact duplicates"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testNoRemoveNamedArgumentsAndSimpleMix(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testNoIntentions(
                """
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret>
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")
                }
                """.trimIndent(),
                "Remove exact duplicates"
            )
            testNoIntentions(
                """
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")<caret>
                }
                """.trimIndent(),
                "Remove exact duplicates"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testRemoveVals(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testIntention(
                """
                dependencies {
                    val versionRef = "2.2.0"
                    api("org.jetbrains.kotlin:kotlin-stdlib:versionRef")<caret>
                    api("org.jetbrains.kotlin:kotlin-stdlib:versionRef")
                    api("org.jetbrains.kotlin:kotlin-stdlib:versionRef")
                }
                """.trimIndent(),
                """
                dependencies {
                    val versionRef = "2.2.0"
                    api("org.jetbrains.kotlin:kotlin-stdlib:versionRef")
                }
                """.trimIndent(),
                "Remove exact duplicates"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testNoRemoveDifferentConfiguration(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testNoIntentions(
                """
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret>
                    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                }
                """.trimIndent(),
                "Remove exact duplicates"
            )
            testNoIntentions(
                """
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret>
                }
                """.trimIndent(),
                "Remove exact duplicates"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testNoRemoveWithConfigurationBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testNoIntentions(
                """
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret>
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0") { exclude("something") }
                }
                """.trimIndent(),
                "Remove exact duplicates"
            )
            testNoIntentions(
                """
                dependencies {
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
                    api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")<caret> { exclude("something") }
                }
                """.trimIndent(),
                "Remove exact duplicates"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testNoRemoveDifferentExtraArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testNoIntentions(
                """
                dependencies {
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")<caret>
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0", ext = "something")
                }
                """.trimIndent(),
                "Remove exact duplicates"
            )
            testNoIntentions(
                """
                dependencies {
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")
                    api(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0", ext = "something")<caret>
                }
                """.trimIndent(),
                "Remove exact duplicates"
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testAnnotationProcessorSeparation(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    implementation("org.projectlombok:lombok:1.18.20")
                    annotationProcessor("org.projectlombok:lombok:1.18.20")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testAnnotationProcessorGrouping(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    implementation("org.projectlombok:lombok:1.18.20")
                    <weak_warning>annotationProcessor("org.projectlombok:lombok:1.18.20")</weak_warning>
                    <weak_warning>annotationProcessor("org.projectlombok:lombok:1.18.20")</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions(KOTLIN_DSL_SCRIPTS_MODEL_IMPORT_SUPPORTED_VERSIONS)
    fun testAnnotationProcessorDifferentGrouping(gradleVersion: GradleVersion) {
        runTest(gradleVersion, withVersionCatalogs = false) {
            testHighlighting(
                """
                dependencies {
                    implementation("org.projectlombok:lombok:1.18.20")
                    <weak_warning>annotationProcessor("org.projectlombok:lombok:1.18.20")</weak_warning>
                    <weak_warning>testAnnotationProcessor("org.projectlombok:lombok:1.18.20")</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions("7.0+", reason = "Gradle 7.0+ supports type-safe project dependencies")
    fun `test different unresolvable accessors are not duplicates`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_TYPE_SAFE_PROJECT_DEPENDENCIES) {
            codeInsightFixture.enableInspections(GradleAvoidDuplicateDependenciesInspection::class.java)
            testHighlighting(
                """
                dependencies {
                    api(projects.subproject1)
                    api(projects.subproject2)
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @AllGradleVersionsSource
    @TargetVersions("7.0+", reason = "Gradle 7.0+ supports type-safe project dependencies")
    fun `test identical unresolvable accessors are duplicates`(gradleVersion: GradleVersion) {
        test(gradleVersion, WITH_TYPE_SAFE_PROJECT_DEPENDENCIES) {
            codeInsightFixture.enableInspections(GradleAvoidDuplicateDependenciesInspection::class.java)
            testHighlighting(
                """
                dependencies {
                    api(projects.subproject1)
                    <weak_warning>api(projects.subproject1.sub)</weak_warning>
                    <weak_warning>api(projects.subproject1.sub)</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    companion object {
        private val WITH_TYPE_SAFE_PROJECT_DEPENDENCIES =
            GradleTestFixtureBuilder.create("with-type-safe-project-dependencies") { gradleVersion ->
                withSettingsFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    setProjectName("with-type-safe-project-dependencies")
                    addCode("enableFeaturePreview(\"TYPESAFE_PROJECT_ACCESSORS\")")
                    include("subproject1", "subproject1:sub", "subproject2")
                }
                withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                    withKotlinJvmPlugin()
                }
                withBuildFile(gradleVersion, "subproject1", gradleDsl = GradleDsl.KOTLIN)
                withBuildFile(gradleVersion, "subproject1/sub", gradleDsl = GradleDsl.KOTLIN)
                withBuildFile(gradleVersion, "subproject2", gradleDsl = GradleDsl.KOTLIN)
            }
    }
}