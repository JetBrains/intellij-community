// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2.inspections

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.codeInspection.GradleAvoidDependencyNamedArgumentsNotationInspection
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.testFramework.GradleTestFixtureBuilder
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.util.withBuildFile
import org.junit.jupiter.params.ParameterizedTest

class KotlinAvoidDependencyNamedArgumentsNotationInspectionTest : K2GradleCodeInsightTestCase() {

    private fun runTest(
        gradleVersion: GradleVersion,
        test: () -> Unit,
    ) {
        test(gradleVersion, CUSTOM_PROJECT) {
            codeInsightFixture.enableInspections(GradleAvoidDependencyNamedArgumentsNotationInspection::class.java)
            test()
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testSingleString(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                dependencies { 
                    implementation("org.gradle:gradle-core:1.0")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testRegular(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                dependencies { 
                    implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END
                }
                """.trimIndent()
            )
            testIntention(
                """
                dependencies { 
                    implementation(group = "org.gradle",<caret> name = "gradle-core", version = "1.0")
                }
                """.trimIndent(),
                """
                dependencies { 
                    implementation("org.gradle:gradle-core:1.0")
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNonLiteralArgument(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                val verRef = "1.0"
                dependencies { 
                    implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = verRef)$WARNING_END
                }
                """.trimIndent()
            )
            testIntention(
                """
                val verRef = "1.0"
                dependencies { 
                    implementation(group = "org.gradle", name = "gradle-core", version = verRef)<caret>
                }
                """.trimIndent(),
                $$"""
                val verRef = "1.0"
                dependencies { 
                    implementation("org.gradle:gradle-core:$verRef")
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNonLiteralArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                val groupRef = "org.gradle"
                val groupRefRef = groupRef
                val nameRef = "gradle-core"
                val verRef = "1.0"
                dependencies { 
                    implementation$WARNING_START(group = groupRefRef, name = nameRef, version = verRef)$WARNING_END
                }
                """.trimIndent()
            )
            testIntention(
                """
                val groupRef = "org.gradle"
                val groupRefRef = groupRef
                val nameRef = "gradle-core"
                val verRef = "1.0"
                dependencies { 
                    implementation(group = groupRefRef, name = nameRef, version = verRef)<caret>
                }
                """.trimIndent(),
                $$"""
                val groupRef = "org.gradle"
                val groupRefRef = groupRef
                val nameRef = "gradle-core"
                val verRef = "1.0"
                dependencies { 
                    implementation("$groupRefRef:$nameRef:$verRef")
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNonLiteralArgumentsRequiringBraces(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                fun getVer() = "1.0"
                dependencies { 
                    val groupObject = object { fun getGroup() = "org.gradle" }
                    implementation$WARNING_START(group = groupObject.getGroup(), name = "gradle-core", version = getVer())$WARNING_END
                }
                """.trimIndent()
            )
            testIntention(
                """
                fun getVer() = "1.0"
                dependencies { 
                    val groupObject = object { fun getGroup() = "org.gradle" }
                    implementation(group = groupObject.getGroup(), name = "gradle-core", version = getVer())<caret>
                }
                """.trimIndent(),
                $$"""
                fun getVer() = "1.0"
                dependencies { 
                    val groupObject = object { fun getGroup() = "org.gradle" }
                    implementation("${groupObject.getGroup()}:gradle-core:${getVer()}")
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testCustomConfiguration(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                val customConf by configurations.creating {}
                dependencies { 
                    customConf$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END
                }
                """.trimIndent()
            )
            testIntention(
                """
                val customConf by configurations.creating {}
                dependencies { 
                    customConf(group = "org.gradle",<caret> name = "gradle-core", version = "1.0")
                }
                """.trimIndent(),
                """
                val customConf by configurations.creating {}
                dependencies { 
                    customConf("org.gradle:gradle-core:1.0")
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testCustomConfigurationString(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                dependencies { 
                    "customConf"$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END
                }
                """.trimIndent()
            )
            testIntention(
                """
                dependencies { 
                    "customConf"(group = "org.gradle",<caret> name = "gradle-core", version = "1.0")
                }
                """.trimIndent(),
                """
                dependencies { 
                    "customConf"("org.gradle:gradle-core:1.0")
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testCustomSourceSet(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                dependencies { 
                    "customSourceSetImplementation"$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END
                }
                """.trimIndent()
            )
            testIntention(
                """
                dependencies { 
                    "customSourceSetImplementation"(group = "org.gradle",<caret> name = "gradle-core", version = "1.0")
                }
                """.trimIndent(),
                """
                dependencies { 
                    "customSourceSetImplementation"("org.gradle:gradle-core:1.0")
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testArgumentWithDollarInterpolation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                $$"""
                val verRef = "1.0"
                dependencies { 
                    implementation$$WARNING_START(group = "org.gradle", name = "gradle-core", version = "$verRef")$$WARNING_END
                }
                """.trimIndent()
            )
            testIntention(
                $$"""
                val verRef = "1.0"
                dependencies { 
                    implementation(group = "org.gradle",<caret> name = "gradle-core", version = "$verRef")
                }
                """.trimIndent(),
                $$"""
                val verRef = "1.0"
                dependencies { 
                    implementation("org.gradle:gradle-core:$verRef")
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testExtraArgument(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                dependencies { 
                    implementation(group = "org.gradle", name = "gradle-core", version = "1.0", configuration = "someConf")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNoVersionArgument(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                dependencies { 
                    implementation$WARNING_START(group = "org.gradle", name = "gradle-core")$WARNING_END
                }
                """.trimIndent()
            )
            testIntention(
                """
                dependencies { 
                    implementation(group = "org.gradle",<caret> name = "gradle-core")
                }
                """.trimIndent(),
                """
                dependencies { 
                    implementation("org.gradle:gradle-core")
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testNoVersionButAnotherArgument(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                dependencies { 
                    implementation(group = "org.gradle", name = "gradle-core", configuration = "someConf")
                }
                """.trimIndent()
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testWithBlock(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                dependencies { 
                    implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END {
                        exclude(group = "com.google.guava", module = "guava")
                    }
                }
                """.trimIndent()
            )
            testIntention(
                """
                dependencies { 
                    implementation(group = "org.gradle",<caret> name = "gradle-core", version = "1.0") {
                        exclude(group = "com.google.guava", module = "guava")
                    }
                }
                """.trimIndent(),
                """
                dependencies { 
                    implementation("org.gradle:gradle-core:1.0") {
                        exclude(group = "com.google.guava", module = "guava")
                    }
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testUnusualArgumentOrder(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                dependencies { 
                    implementation$WARNING_START(version = "1.0", group = "org.gradle", name = "gradle-core")$WARNING_END
                }
                """.trimIndent()
            )
            testIntention(
                """
                dependencies { 
                    implementation(version = "1.0",<caret> group = "org.gradle", name = "gradle-core")
                }
                """.trimIndent(),
                """
                dependencies { 
                    implementation("org.gradle:gradle-core:1.0")
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testRawStringArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                dependencies { 
                    implementation$WARNING_START(group = ${'"'}""org.gradle""${'"'}, name = ${'"'}""gradle-core""${'"'}, version = ${'"'}""1.0""${'"'})$WARNING_END
                }
                """.trimIndent()
            )
            testIntention(
                """
                dependencies { 
                    implementation(group = ${'"'}""org.gradle""${'"'},<caret> name = ${'"'}""gradle-core""${'"'}, version = ${'"'}""1.0""${'"'})
                }
                """.trimIndent(),
                """
                dependencies { 
                    implementation("org.gradle:gradle-core:1.0")
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testMultiDollarInterpolation(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                $$$"""
                val gradle = "org.gradle"
                dependencies { 
                    implementation$$$WARNING_START(group = $$${'"'}""$gradle""$$${'"'}, name = $$$$${'"'}""gr$dle-core""$$${'"'}, version = "1.0")$$$WARNING_END
                }
                """.trimIndent()
            )
            testIntention(
                $$$"""
                val gradle = "org.gradle"
                dependencies { 
                    implementation(group = $$${'"'}""$gradle""$$${'"'},<caret> name = $$$$${'"'}""gr$dle-core""$$${'"'}, version = "1.0")
                }
                """.trimIndent(),
                $$$"""
                val gradle = "org.gradle"
                dependencies { 
                    implementation("$gradle:gr${'$'}dle-core:1.0")
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testPositionalArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                dependencies { 
                    implementation$WARNING_START("org.gradle", "gradle-core", "1.0")$WARNING_END
                }
                """.trimIndent()
            )
            testIntention(
                """
                dependencies { 
                    implementation(<caret>"org.gradle", "gradle-core", "1.0")
                }
                """.trimIndent(),
                """
                dependencies { 
                    implementation("org.gradle:gradle-core:1.0")
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testMixPositionalAndNamedArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                dependencies { 
                    implementation$WARNING_START("org.gradle", "gradle-core", version = "1.0")$WARNING_END
                }
                """.trimIndent()
            )
            testIntention(
                """
                dependencies { 
                    implementation(<caret>"org.gradle", "gradle-core", version = "1.0")
                }
                """.trimIndent(),
                """
                dependencies { 
                    implementation("org.gradle:gradle-core:1.0")
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    @ParameterizedTest
    @BaseGradleVersionSource
    fun testMultipleDependenciesBlocks(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                dependencies { 
                    implementation$WARNING_START(group = "org.gradle", name = "gradle-core", version = "1.0")$WARNING_END
                }
                dependencies { 
                    implementation$WARNING_START(group = "org.gradle", name = "gradle-core-other", version = "1.0")$WARNING_END
                }
                """.trimIndent()
            )
            testIntention(
                """
                dependencies { 
                    implementation(group = "org.gradle", name = "gradle-core", version = "1.0")<caret>
                }
                dependencies { 
                    implementation(group = "org.gradle", name = "gradle-core-other", version = "1.0")
                }
                """.trimIndent(),
                """
                dependencies { 
                    implementation("org.gradle:gradle-core:1.0")
                }
                dependencies { 
                    implementation(group = "org.gradle", name = "gradle-core-other", version = "1.0")
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    // conversion to a single string does not look nice, so don't offer a QF
    @ParameterizedTest
    @BaseGradleVersionSource
    fun testMultilineRawStringArguments(gradleVersion: GradleVersion) {
        runTest(gradleVersion) {
            testHighlighting(
                """
                dependencies { 
                    implementation$WARNING_START(group = ${'"'}""
                    org.gradle
                    ""${'"'}, name = ${'"'}""gradle-core""${'"'}, version = "1.0")$WARNING_END
                }
                """.trimIndent()
            )
            testNoIntentions(
                """
                dependencies { 
                    implementation(group = ${'"'}""
                    org.gradle
                    ""${'"'}, name = ${'"'}""gradle-core""${'"'}, version = "1.0")<caret>
                }
                """.trimIndent(),
                "Simplify"
            )
        }
    }

    companion object {
        private const val WARNING_START = "<weak_warning>"
        private const val WARNING_END = "</weak_warning>"
        private val CUSTOM_PROJECT = GradleTestFixtureBuilder.create("avoid_named_arguments") { gradleVersion ->
            withBuildFile(gradleVersion, gradleDsl = GradleDsl.KOTLIN) {
                withJavaPlugin()
                withPrefix {
                    code("val customSourceSet by sourceSets.creating {}")
                    code("val customConf by configurations.creating {}")
                }
            }
        }
    }
}