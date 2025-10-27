// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.gradle.scripting.k2.K2GradleCodeInsightTestCase
import org.jetbrains.plugins.gradle.codeInspection.GradleAvoidDuplicateDependenciesInspection
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.junit.jupiter.params.ParameterizedTest

class KotlinAvoidDuplicateDependenciesInspectionTest : K2GradleCodeInsightTestCase() {

    private fun runTest(
        gradleVersion: GradleVersion,
        test: () -> Unit
    ) {
        test(gradleVersion, PROJECT_FIXTURE) {
            codeInsightFixture.enableInspections(GradleAvoidDuplicateDependenciesInspection::class.java)
            test()
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testSingleDependency(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testDifferentDependencies(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testSimpleSameDependency(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testSameDependencyDifferentConfigurations(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testSameDependencyInDifferentBlocks(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testSameDependencyConfigurationBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testSameDependencyNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testSameDependencyNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testDifferentVersions(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testSimpleVersionCatalogsResolve(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testSimpleVersionCatalogsResolveNoVersion(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testVersionCatalogsResolve(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testVersionCatalogsResolveVersionRef(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testVersionCatalogsResolveMultiline(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testSameVersionVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testStringInterpolation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testNamedArgumentsVals(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testCoordinateVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testDifferentVersionSameNameVal(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testDifferentVersionSameNameValNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testCustomConfigurationSimple(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testCustomConfigurationNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                val customConf by configurations.creating {}
                dependencies {
                    <weak_warning>api("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")</weak_warning>
                    <weak_warning>customConf(group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0")</weak_warning>
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testCustomConfigurationVersionCatalog(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testSameKotlinArg(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testDifferentKotlinArg(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testKotlinArgVar(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testSameKotlinArgDifferentVals(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testIgnoreOtherDependencyBlocks(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testRemoveSimple(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testRemoveSimpleMultiple(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testRemoveSimpleUnselected(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testRemoveNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testNoRemoveNamedArgumentsAndSimpleMix(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testRemoveVals(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testNoRemoveDifferentConfiguration(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testNoRemoveWithConfigurationBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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
    @BaseGradleVersionSource
    fun testNoRemoveDifferentExtraArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
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

    companion object {
        private val PROJECT_FIXTURE = GradleTestFixtureBuilder.create("my_project_fixture") { gradleVersion ->
            withFile(
                "gradle/libs.versions.toml", /* language=TOML */ """
                [versions]
                kotlin = "2.2.0"
                [libraries]
                kotlin-std-lib-simple = "org.jetbrains.kotlin:kotlin-stdlib:2.2.0"
                kotlin-std-lib-noVersion.module = "org.jetbrains.kotlin:kotlin-stdlib"
                kotlin-std-lib-moduleVersion = { module = "org.jetbrains.kotlin:kotlin-stdlib", version = "2.2.0" }
                kotlin-std-lib-moduleVersionRef = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
                kotlin-std-lib-groupNameVersion = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version = "2.2.0" }
                kotlin-std-lib-groupNameVersionRef = { group = "org.jetbrains.kotlin", name = "kotlin-stdlib", version.ref = "kotlin" }
                kotlin-std-lib-multiline = ""${'"'}org.jetbrains.kotlin
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
    }
}